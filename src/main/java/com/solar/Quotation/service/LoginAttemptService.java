package com.solar.Quotation.service;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.entity.LoginThrottle;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.repository.LoginThrottleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.LogMask;
import com.solar.Quotation.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Login rate limiting / lockout, per typed username (whether or not it exists, so nothing leaks) and per IP.
 * Failures are written in their OWN transaction: the login transaction rolls back when it throws, and the failed
 * attempt (counter + audit row) must survive that. Counters are in MySQL, so every app instance shares them.
 * Logs never contain passwords or tokens; the typed login is masked.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    static final String USER = "USER";
    static final String IP = "IP";
    static final String RESET_IP = "RESET_IP";

    private enum Result { OK, LOCKED_NOW, ALREADY_LOCKED }

    private final LoginThrottleRepository repo;
    private final TokenUtil tokenUtil;
    private final AuditService auditService;
    private final int maxPerUser;
    private final int maxPerIp;
    private final long windowMinutes;
    private final long lockMinutes;
    private final int resetMaxPerHour;

    public LoginAttemptService(LoginThrottleRepository repo, TokenUtil tokenUtil, AuditService auditService,
                               @Value("${app.security.login.max-failures-per-user:5}") int maxPerUser,
                               @Value("${app.security.login.max-failures-per-ip:20}") int maxPerIp,
                               @Value("${app.security.login.window-minutes:15}") long windowMinutes,
                               @Value("${app.security.login.lock-minutes:15}") long lockMinutes,
                               @Value("${app.security.reset.max-requests-per-ip-hour:10}") int resetMaxPerHour) {
        this.repo = repo;
        this.tokenUtil = tokenUtil;
        this.auditService = auditService;
        this.maxPerUser = maxPerUser;
        this.maxPerIp = maxPerIp;
        this.windowMinutes = windowMinutes;
        this.lockMinutes = lockMinutes;
        this.resetMaxPerHour = resetMaxPerHour;
    }

    /** Throws 429 TOO_MANY_ATTEMPTS while the typed login or the IP is locked. Call BEFORE checking the password. */
    @Transactional
    public void assertNotLocked(String login, String ip) {
        LocalDateTime now = now();
        String reason = "LOCKED_USER";
        LocalDateTime until = lockedUntil(USER, login, now);
        if (until == null) {
            until = lockedUntil(IP, ip, now);
            reason = "LOCKED_IP";
        }
        if (until != null) {
            long mins = Math.max(1, Duration.between(now, until).toMinutes() + 1);
            log.warn("[AUTH] login blocked reason={} user={} ip={}", reason, LogMask.login(login), LogMask.ip(ip));
            throw new BusinessException("TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Please try again in " + mins + " minute" + (mins == 1 ? "" : "s") + ".",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Counts the failure, logs it, writes the audit row. Own transaction so it survives the caller's rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String login, String ip, String reason, Long userId, Long partnerId) {
        log.warn("[AUTH] login failed reason={} user={} ip={}", reason, LogMask.login(login), LogMask.ip(ip));
        Result byUser = bump(USER, login, maxPerUser, windowMinutes, lockMinutes);
        Result byIp = bump(IP, ip, maxPerIp, windowMinutes, lockMinutes);

        audit(AuditAction.LOGIN_FAILED, userId, LogMask.login(login), partnerId, "reason=" + reason + " ip=" + LogMask.ip(ip));
        if (byUser == Result.LOCKED_NOW) {
            log.warn("[AUTH] lockout scope=USER user={} ip={} minutes={}", LogMask.login(login), LogMask.ip(ip), lockMinutes);
            audit(AuditAction.ACCOUNT_LOCKED, userId, LogMask.login(login), partnerId,
                    "scope=USER minutes=" + lockMinutes + " ip=" + LogMask.ip(ip));
        }
        if (byIp == Result.LOCKED_NOW) {
            log.warn("[AUTH] lockout scope=IP ip={} minutes={}", LogMask.ip(ip), lockMinutes);
            audit(AuditAction.ACCOUNT_LOCKED, null, "ip:" + LogMask.ip(ip), null,
                    "scope=IP minutes=" + lockMinutes);
        }
    }

    /** A successful login (or a completed password reset) clears the per-username counters. */
    @Transactional
    public void clearUser(String... logins) {
        if (logins == null) return;
        for (String l : logins) {
            if (l != null && !l.isBlank()) repo.deleteOne(USER, hash(USER, l));
        }
    }

    /** Forgot-password flood control per IP. Returns false when this IP should be ignored (answer stays generic). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerResetRequest(String ip) {
        return bump(RESET_IP, ip, resetMaxPerHour + 1, 60, 60) == Result.OK;
    }

    // ------------------------------------------------------------------ internals

    private LocalDateTime lockedUntil(String scope, String subject, LocalDateTime now) {
        return repo.findByScopeAndSubjectHash(scope, hash(scope, subject))
                .map(LoginThrottle::getLockedUntil)
                .filter(u -> u != null && u.isAfter(now))
                .orElse(null);
    }

    /**
     * Row-locked read-modify-write. Two requests racing to create the very first row hit the unique constraint:
     * AuthServiceImpl treats a failure here as "could not record" and still answers the login normally.
     */
    private Result bump(String scope, String subject, int max, long window, long lock) {
        String h = hash(scope, subject);
        LocalDateTime now = now();
        LoginThrottle t = repo.findForUpdate(scope, h).orElse(null);
        if (t == null) {
            t = new LoginThrottle();
            t.setScope(scope);
            t.setSubjectHash(h);
            t.setFailures(0);
            t.setWindowStart(now);
        }
        if (t.getLockedUntil() != null && t.getLockedUntil().isAfter(now)) {
            return Result.ALREADY_LOCKED;
        }
        if (t.getWindowStart() == null || t.getWindowStart().plusMinutes(window).isBefore(now)) {
            t.setFailures(0);
            t.setWindowStart(now);
            t.setLockedUntil(null);
        }
        t.setFailures(t.getFailures() + 1);
        Result result = Result.OK;
        if (t.getFailures() >= max) {
            t.setLockedUntil(now.plusMinutes(lock));
            t.setFailures(0);
            t.setWindowStart(now);
            result = Result.LOCKED_NOW;
        }
        try {
            repo.save(t);
        } catch (DataIntegrityViolationException e) {
            log.debug("[AUTH] throttle row created concurrently, attempt not counted");
        }
        return result;
    }

    private void audit(AuditAction action, Long userId, String name, Long partnerId, String details) {
        auditService.recordEvent((CurrentUser) null, action, "AUTH", "AppUser", userId, name, partnerId, details);
    }

    private String hash(String scope, String subject) {
        String s = subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT);
        return tokenUtil.sha256(scope + ":" + s);
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}