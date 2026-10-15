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
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.AuthService;
import com.solar.Quotation.util.TokenUtil;
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
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Pattern PASSWORD_RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,100}$");

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

    public AuthServiceImpl(AppUserRepository userRepository, ChannelPartnerRepository partnerRepository,
                           RoleRepository roleRepository, ModuleRepository moduleRepository,
                           PermissionRepository permissionRepository, RefreshTokenRepository refreshTokenRepository,
                           PermissionCache permissionCache, JwtUtil jwtUtil, TokenUtil tokenUtil,
                           PasswordEncoder passwordEncoder, AuditService auditService) {
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
    }

    // ---------------------------------------------------------------- login

    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto req) {
        // One message for unknown user and wrong password, so usernames cannot be probed.
        AppUser user = userRepository.findByUsernameIgnoreCaseAndDeletedFalse(req.getUsername().trim())
                .orElseThrow(AuthServiceImpl::invalidCredentials);
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        ChannelPartner partner = requireActive(user);
        Role role = requireRole(user);

        user.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        String accessToken = jwtUtil.createAccessToken(user.getId(), role.getCode(), partner.getId());
        LocalDateTime refreshExpiry = LocalDateTime.now(ZoneOffset.UTC).plusDays(tokenUtil.refreshDays(req.isRememberMe()));
        String refreshToken = issueRefreshToken(user.getId(), refreshExpiry);

        CurrentUser actor = new CurrentUser(user.getId(), user.getName(), role.getId(), role.getCode(),
                role.getDataScope(), user.getChannelPartnerId());
        auditService.recordAs(actor, AuditAction.LOGIN, "AUTH", "AppUser", user.getId(), user.getUsername(),
                partner.getId(), null);

        return new LoginResponseDto(accessToken, refreshToken, jwtUtil.getAccessSeconds(),
                buildSession(user, role, partner));
    }

    /** noRollbackFor: when a used token is replayed we revoke every token of that user and must keep that change. */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponseDto refresh(RefreshTokenRequestDto req) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenUtil.sha256(req.getRefreshToken()))
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Invalid refresh token",
                        HttpStatus.UNAUTHORIZED));

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(stored.getUserId());      // replay detected
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token already used", HttpStatus.UNAUTHORIZED);
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException("TOKEN_EXPIRED", "Refresh token expired", HttpStatus.UNAUTHORIZED);
        }

        AppUser user = userRepository.findByIdAndDeletedFalse(stored.getUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User no longer exists", HttpStatus.UNAUTHORIZED));
        ChannelPartner partner = requireActive(user);
        Role role = requireRole(user);

        stored.setRevoked(true);                                              // rotation: one use only
        refreshTokenRepository.save(stored);
        String newRefresh = issueRefreshToken(user.getId(), stored.getExpiresAt());   // absolute lifetime, not extended
        String access = jwtUtil.createAccessToken(user.getId(), role.getCode(), partner.getId());
        return new TokenResponseDto(access, newRefresh, jwtUtil.getAccessSeconds());
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
        auditService.record(AuditAction.LOGOUT, "AUTH", "AppUser", current.getUserId(), current.getName(),
                current.getPartnerId(), null);
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
            throw new BusinessException("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("VALIDATION_ERROR", "New password and confirm password do not match");
        }
        if (!PASSWORD_RULE.matcher(req.getNewPassword()).matches()) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Password must be at least 8 characters and contain a letter and a digit");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllForUser(user.getId());      // every other device must log in again
        auditService.record(AuditAction.PASSWORD_RESET, "AUTH", "AppUser", user.getId(), user.getUsername(),
                user.getChannelPartnerId(), null);
    }

    // ---------------------------------------------------------------- helpers

    private static BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.UNAUTHORIZED);
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
