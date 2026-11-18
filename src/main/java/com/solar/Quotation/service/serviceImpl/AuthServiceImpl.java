package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.ChangePasswordRequestDto;
import com.solar.Quotation.dto.requestdto.LoginRequestDto;
import com.solar.Quotation.dto.requestdto.RefreshTokenRequestDto;
import com.solar.Quotation.dto.responsedto.CurrentUserResponseDto;
import com.solar.Quotation.dto.responsedto.LoginResponseDto;
import com.solar.Quotation.dto.responsedto.ModuleAccessResponseDto;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.dto.responsedto.RoleRefResponseDto;
import com.solar.Quotation.dto.responsedto.TokenResponseDto;
import com.solar.Quotation.dto.responsedto.UserInfoResponseDto;
import com.solar.Quotation.entity.AppModule;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Permission;
import com.solar.Quotation.entity.RefreshToken;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.ModuleRepository;
import com.solar.Quotation.repository.PermissionRepository;
import com.solar.Quotation.repository.RefreshTokenRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.JwtUtil;
import com.solar.Quotation.security.LogMask;
import com.solar.Quotation.security.PasswordPolicy;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.AuthService;
import com.solar.Quotation.service.LoginAttemptService;
import com.solar.Quotation.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AppUserRepository userRepository;
    private final ChannelPartnerRepository partnerRepository;
    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PermissionCache permissionCache;
    private final JwtUtil jwtUtil;
    private final TokenUtil tokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LoginAttemptService attempts;
    /** Verified against when the login does not exist, so "unknown user" takes as long as "wrong password". */
    private final String dummyHash;

    public AuthServiceImpl(AppUserRepository userRepository, ChannelPartnerRepository partnerRepository,
                           RoleRepository roleRepository, ModuleRepository moduleRepository,
                           PermissionRepository permissionRepository, RefreshTokenRepository refreshTokenRepository,
                           PermissionCache permissionCache, JwtUtil jwtUtil, TokenUtil tokenUtil,
                           PasswordEncoder passwordEncoder, AuditService auditService,
                           LoginAttemptService attempts) {
        this.userRepository = userRepository;
        this.partnerRepository = partnerRepository;
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
        this.permissionRepository = permissionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.permissionCache = permissionCache;
        this.jwtUtil = jwtUtil;
        this.tokenUtil = tokenUtil;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.attempts = attempts;
        this.dummyHash = passwordEncoder.encode("timing-" + UUID.randomUUID());
    }

    // ---------------------------------------------------------------- login

    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto req, String clientIp) {
        String login = req.getUsername().trim();
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;

        attempts.assertNotLocked(login, ip);                       // 429 before any password work

        // One answer for unknown user and wrong password, so usernames cannot be probed.
        AppUser user = userRepository.findByLogin(login).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.getPassword(), dummyHash);
            throw failLogin(login, ip, "UNKNOWN_USER", null, null);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw failLogin(login, ip, "BAD_PASSWORD", user.getId(), user.getChannelPartnerId());
        }

        // Only a correct password learns that the account / partner / role is blocked.
        ChannelPartner partner = partnerRepository.findByIdAndDeletedFalse(user.getChannelPartnerId()).orElse(null);
        Role role = roleRepository.findByIdAndDeletedFalse(user.getRoleId()).orElse(null);
        String blocked = blockReason(user, partner, role);
        if (blocked != null) {
            failLogin(login, ip, blocked, user.getId(), user.getChannelPartnerId());
            throw new ForbiddenException(blocked, blockMessage(blocked));
        }

        attempts.clearUser(login, user.getUsername(), user.getEmail());
        user.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        String accessToken = jwtUtil.createAccessToken(user.getId(), role.getCode(), partner.getId());
        LocalDateTime refreshExpiry = LocalDateTime.now(ZoneOffset.UTC).plusDays(tokenUtil.refreshDays(req.isRememberMe()));
        String refreshToken = issueRefreshToken(user.getId(), refreshExpiry);

        CurrentUser actor = new CurrentUser(user.getId(), user.getName(), role.getId(), role.getCode(),
                role.getDataScope(), user.getChannelPartnerId());
        auditService.recordEvent(actor, AuditAction.LOGIN, "AUTH", "AppUser", user.getId(), user.getUsername(),
                partner.getId(), "ip=" + LogMask.ip(ip));
        log.info("[AUTH] login success user={} ip={} userId={} role={} mustChangePassword={}",
                LogMask.login(login), LogMask.ip(ip), user.getId(), role.getCode(), user.isMustChangePassword());

        return new LoginResponseDto(accessToken, refreshToken, jwtUtil.getAccessSeconds(),
                buildSession(user, role, partner));
    }

    /**
     * noRollbackFor: a replayed token or a deactivated account revokes every token of that user and that change must
     * be kept even though the call ends with an error.
     */
    @Override
    @Transactional(noRollbackFor = {BusinessException.class, ForbiddenException.class})
    public TokenResponseDto refresh(RefreshTokenRequestDto req) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenUtil.sha256(req.getRefreshToken()))
                .orElseThrow(() -> {
                    log.warn("[AUTH] refresh rejected reason=UNKNOWN_TOKEN");
                    return new BusinessException("INVALID_REFRESH_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED);
                });
        Long userId = stored.getUserId();

        if (stored.isRevoked()) {
            return replayDetected(userId);
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            log.info("[AUTH] refresh rejected reason=EXPIRED userId={}", userId);
            throw new BusinessException("TOKEN_EXPIRED", "Refresh token expired", HttpStatus.UNAUTHORIZED);
        }

        AppUser user = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
        if (user == null) {
            refreshTokenRepository.revokeAllForUser(userId);
            log.warn("[AUTH] refresh rejected reason=USER_GONE userId={}", userId);
            throw new BusinessException("UNAUTHORIZED", "User no longer exists", HttpStatus.UNAUTHORIZED);
        }
        ChannelPartner partner;
        Role role;
        try {
            partner = requireActive(user);
            role = requireRole(user);
        } catch (ForbiddenException e) {
            refreshTokenRepository.revokeAllForUser(userId);          // deactivated: no session survives
            log.warn("[AUTH] refresh blocked: account, partner or role deactivated userId={}, all sessions revoked", userId);
            throw e;
        }

        // Rotation: one use only. The conditional UPDATE is atomic, so two parallel requests cannot both win.
        if (refreshTokenRepository.revokeIfActive(stored.getId()) == 0) {
            return replayDetected(userId);
        }
        String newRefresh = issueRefreshToken(userId, stored.getExpiresAt());   // absolute lifetime, not extended
        String access = jwtUtil.createAccessToken(userId, role.getCode(), partner.getId());
        log.info("[AUTH] refresh ok userId={}", userId);
        return new TokenResponseDto(access, newRefresh, jwtUtil.getAccessSeconds());
    }

    private TokenResponseDto replayDetected(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
        log.warn("[AUTH] refresh token reuse detected userId={}, all sessions revoked", userId);
        auditService.recordEvent((CurrentUser) null, AuditAction.TOKEN_REUSE_DETECTED, "AUTH", "AppUser", userId,
                "userId=" + userId, null, "all sessions revoked");
        throw new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token already used", HttpStatus.UNAUTHORIZED);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequestDto req) {
        CurrentUser current = requireCurrent();
        refreshTokenRepository.findByTokenHash(tokenUtil.sha256(req.getRefreshToken()))
                .filter(t -> t.getUserId().equals(current.getUserId()))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
        auditService.recordEvent(current, AuditAction.LOGOUT, "AUTH", "AppUser", current.getUserId(), current.getName(),
                current.getPartnerId(), (String) null);
        log.info("[AUTH] logout userId={}", current.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponseDto me() {
        CurrentUser current = requireCurrent();
        AppUser user = userRepository.findByIdAndDeletedFalse(current.getUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User no longer exists", HttpStatus.UNAUTHORIZED));
        return buildSession(user, requireRole(user), requireActive(user));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequestDto req) {
        CurrentUser current = requireCurrent();
        AppUser user = userRepository.findByIdAndDeletedFalse(current.getUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User no longer exists", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("[AUTH] password change rejected reason=BAD_CURRENT_PASSWORD userId={}", user.getId());
            throw new BusinessException("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("VALIDATION_ERROR", "New password and confirm password do not match");
        }
        if (req.getNewPassword().equals(req.getCurrentPassword())) {
            throw new BusinessException("VALIDATION_ERROR", "New password must be different from the current password");
        }
        PasswordPolicy.require(req.getNewPassword(), PasswordPolicy.MIN_USER,
                user.getUsername(), PasswordPolicy.localPart(user.getEmail()));

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setTokenValidAfter(LocalDateTime.now(ZoneOffset.UTC));      // also kills the access token in use
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllForUser(user.getId());           // every device must log in again
        java.util.Map<String, Object> changed = new java.util.LinkedHashMap<>();
        changed.put("method", "change password");
        changed.put("sessionsRevoked", true);
        auditService.record(AuditAction.PASSWORD_RESET, "AUTH", "AppUser", user.getId(), user.getUsername(),
                user.getChannelPartnerId(), null, changed);
        log.info("[AUTH] password changed userId={}, all sessions revoked", user.getId());
    }

    // ---------------------------------------------------------------- helpers

    private static BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    /** Records the failure (own transaction, never breaks the response) and returns the generic error to throw. */
    private BusinessException failLogin(String login, String ip, String reason, Long userId, Long partnerId) {
        try {
            attempts.recordFailure(login, ip, reason, userId, partnerId);
        } catch (RuntimeException e) {
            log.error("[AUTH] could not record failed login reason={}: {}", reason, e.toString());
        }
        return invalidCredentials();
    }

    private static String blockReason(AppUser u, ChannelPartner p, Role r) {
        if (u.getStatus() != RecordStatus.ACTIVE) return "ACCOUNT_INACTIVE";
        if (p == null || p.getStatus() != RecordStatus.ACTIVE) return "PARTNER_INACTIVE";
        if (r == null || r.getStatus() != RecordStatus.ACTIVE) return "ROLE_INACTIVE";
        return null;
    }

    private static String blockMessage(String reason) {
        switch (reason) {
            case "ACCOUNT_INACTIVE": return "Your account is inactive";
            case "PARTNER_INACTIVE": return "Your channel partner is inactive";
            default: return "Your role is not active";
        }
    }

    private CurrentUser requireCurrent() {
        CurrentUser current = TenantContext.get();
        if (current == null) {
            throw new BusinessException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return current;
    }

    /** User and partner must both be active. Returns the partner. */
    private ChannelPartner requireActive(AppUser user) {
        if (user.getStatus() != RecordStatus.ACTIVE) {
            throw new ForbiddenException("ACCOUNT_INACTIVE", "Your account is inactive");
        }
        ChannelPartner partner = partnerRepository.findByIdAndDeletedFalse(user.getChannelPartnerId())
                .orElseThrow(() -> new ForbiddenException("PARTNER_INACTIVE", "Your channel partner is inactive"));
        if (partner.getStatus() != RecordStatus.ACTIVE) {
            throw new ForbiddenException("PARTNER_INACTIVE", "Your channel partner is inactive");
        }
        return partner;
    }

    private Role requireRole(AppUser user) {
        Role role = roleRepository.findByIdAndDeletedFalse(user.getRoleId())
                .orElseThrow(() -> new ForbiddenException("ROLE_INACTIVE", "Your role no longer exists"));
        if (role.getStatus() != RecordStatus.ACTIVE) {
            throw new ForbiddenException("ROLE_INACTIVE", "Your role is not active");
        }
        return role;
    }

    private String issueRefreshToken(Long userId, LocalDateTime expiresAt) {
        String raw = tokenUtil.generateRefreshToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(tokenUtil.sha256(raw));
        token.setExpiresAt(expiresAt);
        token.setRevoked(false);
        refreshTokenRepository.save(token);
        return raw;
    }

    /** User, sidebar modules and flat permission list: everything the frontend needs to draw itself. */
    private CurrentUserResponseDto buildSession(AppUser user, Role role, ChannelPartner partner) {
        UserInfoResponseDto info = new UserInfoResponseDto();
        info.setId(user.getId());
        info.setName(user.getName());
        info.setEmail(user.getEmail());
        info.setUsername(user.getUsername());
        info.setPhotoUrl(user.getPhotoPath());
        info.setMustChangePassword(user.isMustChangePassword());
        info.setRole(new RoleRefResponseDto(role.getId(), role.getCode(), role.getName(), role.getDataScope().name()));
        info.setPartner(new PartnerRefResponseDto(partner.getId(), partner.getCode(), partner.getName()));

        Set<String> granted = permissionCache.permissionsForRole(role.getId());
        List<AppModule> modules = moduleRepository.findByDeletedFalseOrderBySortOrderAsc();
        List<Permission> catalog = permissionRepository.findByDeletedFalseOrderByIdAsc();

        Map<Long, List<String>> actionsByModule = new HashMap<>();
        List<String> permissionList = new ArrayList<>();
        for (Permission p : catalog) {
            if (granted.contains(p.getCode())) {
                actionsByModule.computeIfAbsent(p.getModuleId(), k -> new ArrayList<>()).add(p.getAction());
                permissionList.add(p.getCode());
            }
        }

        List<ModuleAccessResponseDto> access = new ArrayList<>();
        for (AppModule m : modules) {
            List<String> actions = actionsByModule.get(m.getId());
            if (actions != null && !actions.isEmpty()) {
                access.add(new ModuleAccessResponseDto(m.getCode(), m.getName(), m.getRoute(), actions));
            }
        }
        return new CurrentUserResponseDto(info, access, permissionList);
    }
}