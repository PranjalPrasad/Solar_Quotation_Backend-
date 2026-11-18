package com.solar.Quotation.service;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.entity.LoginThrottle;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.repository.LoginThrottleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock LoginThrottleRepository repo;
    @Mock TokenUtil tokenUtil;
    @Mock AuditService auditService;

    private final Map<String, LoginThrottle> table = new HashMap<>();
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        when(tokenUtil.sha256(anyString())).thenAnswer(i -> i.getArgument(0));
        when(repo.findForUpdate(anyString(), anyString())).thenAnswer(i -> Optional.ofNullable(table.get(i.getArgument(0) + "|" + i.getArgument(1))));
        when(repo.findByScopeAndSubjectHash(anyString(), anyString())).thenAnswer(i -> Optional.ofNullable(table.get(i.getArgument(0) + "|" + i.getArgument(1))));
        when(repo.save(any(LoginThrottle.class))).thenAnswer(i -> {
            LoginThrottle t = i.getArgument(0);
            table.put(t.getScope() + "|" + t.getSubjectHash(), t);
            return t;
        });
        doAnswer(i -> table.remove(i.getArgument(0) + "|" + i.getArgument(1)) == null ? 0 : 1)
                .when(repo).deleteOne(anyString(), anyString());
        // 5 per user, 20 per IP, 15 minute window, 15 minute lock, 10 reset requests per hour
        service = new LoginAttemptService(repo, tokenUtil, auditService, 5, 20, 15, 15, 10);
    }

    private void fail(String login, String ip, int times) {
        for (int i = 0; i < times; i++) service.recordFailure(login, ip, "BAD_PASSWORD", 1L, 7L);
    }

    @Test
    void notLockedBelowThreshold() {
        fail("bob", "10.0.0.1", 4);
        assertDoesNotThrow(() -> service.assertNotLocked("bob", "10.0.0.1"));
    }

    @Test
    void locksUsernameAfterFiveFailures() {
        fail("bob", "10.0.0.1", 5);
        BusinessException e = assertThrows(BusinessException.class, () -> service.assertNotLocked("bob", "10.0.0.2"));
        assertTrue(e.getMessage().contains("Too many failed attempts"));
    }

    @Test
    void usernameMatchIsCaseInsensitive() {
        fail("Bob", "10.0.0.1", 5);
        assertThrows(BusinessException.class, () -> service.assertNotLocked("bOB", "10.0.0.9"));
    }

    @Test
    void lockoutIsPerUsername() {
        fail("bob", "10.0.0.1", 5);
        assertDoesNotThrow(() -> service.assertNotLocked("alice", "10.0.0.2"));
    }

    @Test
    void unknownUsernamesAreThrottledToo() {
        service.recordFailure("ghost", "10.0.0.1", "UNKNOWN_USER", null, null);
        fail("ghost", "10.0.0.1", 4);
        assertThrows(BusinessException.class, () -> service.assertNotLocked("ghost", "10.0.0.5"));
    }

    @Test
    void locksIpAcrossManyUsernames() {
        for (int i = 0; i < 20; i++) service.recordFailure("user" + i, "6.6.6.6", "BAD_PASSWORD", null, null);
        assertThrows(BusinessException.class, () -> service.assertNotLocked("someone-new", "6.6.6.6"));
        assertDoesNotThrow(() -> service.assertNotLocked("someone-new", "7.7.7.7"));
    }

    @Test
    void successfulLoginClearsUserCounter() {
        fail("bob", "10.0.0.1", 4);
        service.clearUser("bob");
        fail("bob", "10.0.0.1", 4);
        assertDoesNotThrow(() -> service.assertNotLocked("bob", "10.0.0.1"));
    }

    @Test
    void resetClearsAnExistingLock() {
        fail("bob", "10.0.0.1", 5);
        service.clearUser("bob", "bob@example.com");
        assertDoesNotThrow(() -> service.assertNotLocked("bob", "10.0.0.3"));
    }

    @Test
    void failedLoginIsWrittenToAuditLog() {
        service.recordFailure("bob@example.com", "10.0.0.1", "BAD_PASSWORD", 1L, 7L);
        verify(auditService).recordAs(isNull(CurrentUser.class), eq(AuditAction.LOGIN_FAILED), eq("AUTH"), eq("AppUser"),
                eq(1L), eq("b***@example.com"), eq(7L), anyString());
    }

    @Test
    void lockoutIsAudited() {
        fail("bob", "10.0.0.1", 5);
        verify(auditService, atLeastOnce()).recordAs(isNull(CurrentUser.class), eq(AuditAction.ACCOUNT_LOCKED), eq("AUTH"),
                eq("AppUser"), any(), anyString(), any(), anyString());
    }

    @Test
    void resetRequestsAreThrottledPerIp() {
        for (int i = 0; i < 9; i++) assertTrue(service.registerResetRequest("9.9.9.9"));
        assertFalse(service.registerResetRequest("9.9.9.9"));
        assertTrue(service.registerResetRequest("8.8.8.8"));
    }
}