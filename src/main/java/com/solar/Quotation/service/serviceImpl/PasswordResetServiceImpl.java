package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.ForgotPasswordRequestDto;
import com.solar.Quotation.dto.requestdto.ResetPasswordRequestDto;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.PasswordResetToken;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.PasswordResetTokenRepository;
import com.solar.Quotation.repository.RefreshTokenRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.LogMask;
import com.solar.Quotation.security.PasswordPolicy;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.EmailService;
import com.solar.Quotation.service.LoginAttemptService;
import com.solar.Quotation.service.PasswordResetService;
import com.solar.Quotation.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Reset links: 48 random bytes, only the SHA-256 hash is stored, single use (claimed with an atomic UPDATE),
 * short expiry (default 15 min), one live link per user, resend cool-down, per-IP flood control.
 * The raw token and the link are never logged.
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    private final AppUserRepository userRepository;
    private final ChannelPartnerRepository partnerRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenUtil tokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final EmailService emailService;
    private final LoginAttemptService attempts;
    private final long expiryMinutes;
    private final String frontendBaseUrl;

    public PasswordResetServiceImpl(AppUserRepository userRepository, ChannelPartnerRepository partnerRepository,
                                    RoleRepository roleRepository, PasswordResetTokenRepository tokenRepository,
                                    RefreshTokenRepository refreshTokenRepository, TokenUtil tokenUtil,
                                    PasswordEncoder passwordEncoder, AuditService auditService,
                                    EmailService emailService, LoginAttemptService attempts,
                                    @Value("${app.password-reset.expiry-minutes:15}") long expiryMinutes,
                                    @Value("${app.frontend.base-url:http://localhost:5500}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.partnerRepository = partnerRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenUtil = tokenUtil;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.emailService = emailService;
        this.attempts = attempts;
        this.expiryMinutes = expiryMinutes;
        this.frontendBaseUrl = frontendBaseUrl.trim().replaceAll("/+$", "");
    }

    // ------------------------------------------------------------ request

    @Override
    @Transactional
    public void requestReset(ForgotPasswordRequestDto req, String clientIp) {
        String identifier = req.getIdentifier().trim();
        String ip = LogMask.ip(clientIp);
        String who = LogMask.login(identifier);

        if (!attempts.registerResetRequest(ip)) {
            log.warn("[AUTH] reset requested outcome=THROTTLED_IP user={} ip={}", who, ip);
            return;                                           // same answer as success
        }
        Optional<AppUser> found = userRepository.findByEmailIgnoreCaseAndDeletedFalse(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCaseAndDeletedFalse(identifier));
        if (found.isEmpty()) {
            log.info("[AUTH] reset requested outcome=UNKNOWN_ACCOUNT user={} ip={}", who, ip);
            return;
        }
        AppUser user = found.get();
        if (!canReset(user)) {
            log.info("[AUTH] reset requested outcome=INACTIVE_ACCOUNT userId={} ip={}", user.getId(), ip);
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (tokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            log.info("[AUTH] reset requested outcome=COOLDOWN userId={} ip={}", user.getId(), ip);
            return;
        }

        tokenRepository.invalidateAllForUser(user.getId());   // older links stop working
        String raw = tokenUtil.generateRefreshToken();        // 48 random bytes, URL-safe
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(tokenUtil.sha256(raw));
        token.setExpiresAt(now.plusMinutes(expiryMinutes));
        token.setUsed(false);
        tokenRepository.save(token);
        log.info("[AUTH] reset requested outcome=SENT userId={} ip={} expiresInMin={}", user.getId(), ip, expiryMinutes);

        String link = frontendBaseUrl + "/reset-password.html?token=" + raw;
        String email = user.getEmail();
        String name = user.getName();
        long minutes = expiryMinutes;

        // send only after the token row is committed
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailService.sendPasswordReset(email, name, link, minutes);
            }
        });
    }

    // ------------------------------------------------------------ reset

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDto req, String clientIp) {
        String ip = LogMask.ip(clientIp);
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("VALIDATION_ERROR", "New password and confirm password do not match");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenUtil.sha256(req.getToken().trim()))
                .orElseThrow(() -> rejected("UNKNOWN_TOKEN", ip));
        if (token.isUsed()) throw rejected("ALREADY_USED", ip);
        if (token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) throw rejected("EXPIRED", ip);
        AppUser user = userRepository.findByIdAndDeletedFalse(token.getUserId())
                .orElseThrow(() -> rejected("USER_GONE", ip));
        if (!canReset(user)) throw rejected("INACTIVE_ACCOUNT", ip);

        // Strength is checked after the link is known to be valid, and BEFORE the link is consumed:
        // a weak password gives an error and the person can try again with the same link.
        PasswordPolicy.require(req.getNewPassword(), PasswordPolicy.MIN_USER,
                user.getUsername(), PasswordPolicy.localPart(user.getEmail()));

        // Atomic claim: of two parallel submissions with the same link exactly one gets 1 row.
        if (tokenRepository.markUsed(token.getId()) == 0) throw rejected("ALREADY_USED", ip);

        Role role = roleRepository.findByIdAndDeletedFalse(user.getRoleId()).orElse(null);
        CurrentUser actor = role == null ? null : new CurrentUser(user.getId(), user.getName(), role.getId(),
                role.getCode(), role.getDataScope(), user.getChannelPartnerId());
        Long userId = user.getId();
        String username = user.getUsername();
        String email = user.getEmail();
        Long partnerId = user.getChannelPartnerId();

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setTokenValidAfter(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.saveAndFlush(user);

        tokenRepository.invalidateAllForUser(userId);          // every other link of this user dies too
        refreshTokenRepository.revokeAllForUser(userId);       // every device must log in again
        attempts.clearUser(username, email);                   // a lockout does not outlive a verified reset
        java.util.Map<String, Object> reset = new java.util.LinkedHashMap<>();
        reset.put("method", "reset link");
        reset.put("ip", ip);
        reset.put("sessionsRevoked", true);
        auditService.recordAs(actor, AuditAction.PASSWORD_RESET, "AUTH", "AppUser", userId, username,
                partnerId, null, reset);
        log.info("[AUTH] reset used userId={} ip={}, all sessions revoked", userId, ip);
    }

    // ------------------------------------------------------------ helpers

    private boolean canReset(AppUser user) {
        return user.getStatus() == RecordStatus.ACTIVE
                && partnerRepository.findByIdAndDeletedFalse(user.getChannelPartnerId())
                .map(p -> p.getStatus() == RecordStatus.ACTIVE).orElse(false);
    }

    private static BusinessException rejected(String reason, String ip) {
        log.warn("[AUTH] reset rejected reason={} ip={}", reason, ip);
        return invalidLink();
    }

    private static BusinessException invalidLink() {
        return new BusinessException("INVALID_RESET_TOKEN",
                "This reset link is invalid or has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
    }
}