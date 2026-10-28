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
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.EmailService;
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
import java.util.regex.Pattern;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    /** Same rule as AuthServiceImpl.changePassword. */
    private static final Pattern PASSWORD_RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,100}$");
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
    private final long expiryMinutes;
    private final String frontendBaseUrl;

    public PasswordResetServiceImpl(AppUserRepository userRepository, ChannelPartnerRepository partnerRepository,
                                    RoleRepository roleRepository, PasswordResetTokenRepository tokenRepository,
                                    RefreshTokenRepository refreshTokenRepository, TokenUtil tokenUtil,
                                    PasswordEncoder passwordEncoder, AuditService auditService,
                                    EmailService emailService,
                                    @Value("${app.password-reset.expiry-minutes:30}") long expiryMinutes,
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
        this.expiryMinutes = expiryMinutes;
        this.frontendBaseUrl = frontendBaseUrl.trim().replaceAll("/+$", "");
    }

    // ------------------------------------------------------------ request

    @Override
    @Transactional
    public void requestReset(ForgotPasswordRequestDto req) {
        String identifier = req.getIdentifier().trim();

        Optional<AppUser> found = userRepository.findByEmailIgnoreCaseAndDeletedFalse(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCaseAndDeletedFalse(identifier));
        if (found.isEmpty()) {
            log.info("Password reset requested for an unknown account");
            return;                                           // same answer as success
        }
        AppUser user = found.get();
        if (!canReset(user)) {
            log.info("Password reset ignored for inactive account id={}", user.getId());
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (tokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            log.info("Password reset ignored (cool-down) for user id={}", user.getId());
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
    public void resetPassword(ResetPasswordRequestDto req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("VALIDATION_ERROR", "New password and confirm password do not match");
        }
        if (!PASSWORD_RULE.matcher(req.getNewPassword()).matches()) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Password must be at least 8 characters and contain a letter and a digit");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenUtil.sha256(req.getToken().trim()))
                .orElseThrow(PasswordResetServiceImpl::invalidLink);
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw invalidLink();
        }
        AppUser user = userRepository.findByIdAndDeletedFalse(token.getUserId())
                .orElseThrow(PasswordResetServiceImpl::invalidLink);
        if (!canReset(user)) {
            throw invalidLink();
        }

        Role role = roleRepository.findByIdAndDeletedFalse(user.getRoleId()).orElse(null);
        CurrentUser actor = role == null ? null : new CurrentUser(user.getId(), user.getName(), role.getId(),
                role.getCode(), role.getDataScope(), user.getChannelPartnerId());
        Long userId = user.getId();
        String username = user.getUsername();
        Long partnerId = user.getChannelPartnerId();

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.saveAndFlush(user);

        tokenRepository.invalidateAllForUser(userId);          // this link and any other link: single use
        refreshTokenRepository.revokeAllForUser(userId);       // every device must log in again
        auditService.recordAs(actor, AuditAction.PASSWORD_RESET, "AUTH", "AppUser", userId, username,
                partnerId, null);
    }

    // ------------------------------------------------------------ helpers

    private boolean canReset(AppUser user) {
        return user.getStatus() == RecordStatus.ACTIVE
                && partnerRepository.findByIdAndDeletedFalse(user.getChannelPartnerId())
                .map(p -> p.getStatus() == RecordStatus.ACTIVE).orElse(false);
    }

    private static BusinessException invalidLink() {
        return new BusinessException("INVALID_RESET_TOKEN",
                "This reset link is invalid or has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
    }
}
