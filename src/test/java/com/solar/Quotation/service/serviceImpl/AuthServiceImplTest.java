package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.ChangePasswordRequestDto;
import com.solar.Quotation.dto.requestdto.LoginRequestDto;
import com.solar.Quotation.dto.requestdto.RefreshTokenRequestDto;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.ChannelPartner;
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
import com.solar.Quotation.service.LoginAttemptService;
import com.solar.Quotation.util.TokenUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock AppUserRepository userRepository;
    @Mock ChannelPartnerRepository partnerRepository;
    @Mock RoleRepository roleRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PermissionCache permissionCache;
    @Mock JwtUtil jwtUtil;
    @Mock TokenUtil tokenUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock LoginAttemptService attempts;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        when(tokenUtil.sha256(anyString())).thenAnswer(i -> "h:" + i.getArgument(0));
        when(tokenUtil.generateRefreshToken()).thenReturn("new-raw");
        when(tokenUtil.refreshDays(anyBoolean())).thenReturn(7L);
        when(jwtUtil.createAccessToken(any(), any(), any())).thenReturn("access");
        when(jwtUtil.getAccessSeconds()).thenReturn(600L);
        service = new AuthServiceImpl(userRepository, partnerRepository, roleRepository, moduleRepository,
                permissionRepository, refreshTokenRepository, permissionCache, jwtUtil, tokenUtil, passwordEncoder,
                auditService, attempts);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ------------------------------------------------------------ fixtures

    private AppUser user(RecordStatus status) {
        AppUser u = mock(AppUser.class);
        when(u.getId()).thenReturn(5L);
        when(u.getStatus()).thenReturn(status);
        when(u.getChannelPartnerId()).thenReturn(7L);
        when(u.getRoleId()).thenReturn(3L);
        when(u.getPasswordHash()).thenReturn("hash");
        when(u.getUsername()).thenReturn("bob");
        when(u.getEmail()).thenReturn("bob@example.com");
        when(userRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(u));
        return u;
    }

    private void partnerAndRole(RecordStatus partnerStatus, RecordStatus roleStatus) {
        ChannelPartner p = mock(ChannelPartner.class);
        when(p.getId()).thenReturn(7L);
        when(p.getStatus()).thenReturn(partnerStatus);
        when(partnerRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(p));
        Role r = mock(Role.class);
        when(r.getId()).thenReturn(3L);
        when(r.getCode()).thenReturn("CHANNEL_PARTNER_ADMIN");
        when(r.getStatus()).thenReturn(roleStatus);
        when(roleRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(r));
    }

    private RefreshToken stored(boolean revoked) {
        RefreshToken t = mock(RefreshToken.class);
        when(t.getId()).thenReturn(11L);
        when(t.getUserId()).thenReturn(5L);
        when(t.isRevoked()).thenReturn(revoked);
        when(t.getExpiresAt()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusDays(3));
        when(refreshTokenRepository.findByTokenHash("h:old")).thenReturn(Optional.of(t));
        return t;
    }

    private RefreshTokenRequestDto refreshReq(String raw) {
        RefreshTokenRequestDto r = mock(RefreshTokenRequestDto.class);
        when(r.getRefreshToken()).thenReturn(raw);
        return r;
    }

    private LoginRequestDto loginReq(String login, String pw) {
        LoginRequestDto r = mock(LoginRequestDto.class);
        when(r.getUsername()).thenReturn(login);
        when(r.getPassword()).thenReturn(pw);
        return r;
    }

    // ------------------------------------------------------------ refresh rotation

    @Test
    void refreshRotatesTokenAndKeepsAbsoluteExpiry() {
        RefreshToken old = stored(false);
        user(RecordStatus.ACTIVE);
        partnerAndRole(RecordStatus.ACTIVE, RecordStatus.ACTIVE);
        when(refreshTokenRepository.revokeIfActive(11L)).thenReturn(1);

        assertNotNull(service.refresh(refreshReq("old")));

        verify(refreshTokenRepository).revokeIfActive(11L);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertEquals("h:new-raw", saved.getValue().getTokenHash());
        assertEquals(old.getExpiresAt(), saved.getValue().getExpiresAt());
        assertFalse(saved.getValue().isRevoked());
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    @Test
    void replayingAUsedRefreshTokenRevokesEveryUserSession() {
        stored(true);
        assertThrows(BusinessException.class, () -> service.refresh(refreshReq("old")));
        verify(refreshTokenRepository).revokeAllForUser(5L);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void lostRotationRaceIsTreatedAsReplay() {
        stored(false);
        user(RecordStatus.ACTIVE);
        partnerAndRole(RecordStatus.ACTIVE, RecordStatus.ACTIVE);
        when(refreshTokenRepository.revokeIfActive(11L)).thenReturn(0);   // somebody else used it a moment ago

        assertThrows(BusinessException.class, () -> service.refresh(refreshReq("old")));
        verify(refreshTokenRepository).revokeAllForUser(5L);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThrows(BusinessException.class, () -> service.refresh(refreshReq("nope")));
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    // ------------------------------------------------------------ deactivated user / partner / role

    @Test
    void refreshForDeactivatedUserIsBlockedAndRevokesAllSessions() {
        stored(false);
        user(RecordStatus.INACTIVE);
        partnerAndRole(RecordStatus.ACTIVE, RecordStatus.ACTIVE);

        assertThrows(ForbiddenException.class, () -> service.refresh(refreshReq("old")));
        verify(refreshTokenRepository).revokeAllForUser(5L);
        verify(refreshTokenRepository, never()).save(any());
        verify(jwtUtil, never()).createAccessToken(any(), any(), any());
    }

    @Test
    void refreshForDeactivatedPartnerIsBlocked() {
        stored(false);
        user(RecordStatus.ACTIVE);
        partnerAndRole(RecordStatus.INACTIVE, RecordStatus.ACTIVE);

        assertThrows(ForbiddenException.class, () -> service.refresh(refreshReq("old")));
        verify(refreshTokenRepository).revokeAllForUser(5L);
    }

    @Test
    void loginForDeactivatedUserIsBlockedAndAuditedAfterCorrectPassword() {
        AppUser u = user(RecordStatus.INACTIVE);
        partnerAndRole(RecordStatus.ACTIVE, RecordStatus.ACTIVE);
        when(userRepository.findByLogin("bob@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> service.login(loginReq("bob@example.com", "pw"), "10.0.0.1"));

        verify(attempts).recordFailure("bob@example.com", "10.0.0.1", "ACCOUNT_INACTIVE", 5L, 7L);
        verify(jwtUtil, never()).createAccessToken(any(), any(), any());
        verify(attempts, never()).clearUser(any());
    }

    @Test
    void loginForDeactivatedPartnerIsBlocked() {
        AppUser u = user(RecordStatus.ACTIVE);
        partnerAndRole(RecordStatus.INACTIVE, RecordStatus.ACTIVE);
        when(userRepository.findByLogin("bob")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> service.login(loginReq("bob", "pw"), "10.0.0.1"));
        verify(attempts).recordFailure("bob", "10.0.0.1", "PARTNER_INACTIVE", 5L, 7L);
    }

    // ------------------------------------------------------------ lockout wiring + failure recording

    @Test
    void lockedLoginNeverReachesThePasswordCheck() {
        doThrow(new BusinessException("TOO_MANY_ATTEMPTS", "Too many failed attempts")).when(attempts).assertNotLocked(any(), any());

        assertThrows(BusinessException.class, () -> service.login(loginReq("bob", "pw"), "10.0.0.1"));
        verifyNoInteractions(userRepository);
        verify(passwordEncoder, never()).matches(any(), eq("hash"));
    }

    @Test
    void wrongPasswordRecordsFailureWithGenericError() {
        AppUser u = user(RecordStatus.ACTIVE);
        when(userRepository.findByLogin("bob")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class, () -> service.login(loginReq("bob", "bad"), "10.0.0.1"));
        assertEquals("Invalid email or password", e.getMessage());
        verify(attempts).recordFailure("bob", "10.0.0.1", "BAD_PASSWORD", 5L, 7L);
    }

    @Test
    void unknownUserGetsTheSameMessageAndIsRecorded() {
        when(userRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.login(loginReq("ghost", "x"), "10.0.0.1"));
        assertEquals("Invalid email or password", e.getMessage());
        verify(attempts).recordFailure("ghost", "10.0.0.1", "UNKNOWN_USER", null, null);
    }

    @Test
    void failureRecordingProblemsDoNotChangeTheAnswer() {
        when(userRepository.findByLogin("ghost")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("db down")).when(attempts).recordFailure(any(), any(), any(), any(), any());

        BusinessException e = assertThrows(BusinessException.class, () -> service.login(loginReq("ghost", "x"), "10.0.0.1"));
        assertEquals("Invalid email or password", e.getMessage());
    }

    // ------------------------------------------------------------ change password / forced change

    private ChangePasswordRequestDto changeReq(String cur, String neu, String conf) {
        ChangePasswordRequestDto r = mock(ChangePasswordRequestDto.class);
        when(r.getCurrentPassword()).thenReturn(cur);
        when(r.getNewPassword()).thenReturn(neu);
        when(r.getConfirmPassword()).thenReturn(conf);
        return r;
    }

    private void signedIn() {
        TenantContext.set(new CurrentUser(5L, "Bob", 3L, "CHANNEL_PARTNER_ADMIN", DataScope.PARTNER, 7L));
    }

    @Test
    void changePasswordClearsForcedFlagAndRevokesEverySession() {
        AppUser u = user(RecordStatus.ACTIVE);
        signedIn();
        when(passwordEncoder.matches("OldPassw0rd-123", "hash")).thenReturn(true);
        when(passwordEncoder.encode("Brand-New-Pass-2026")).thenReturn("newhash");

        service.changePassword(changeReq("OldPassw0rd-123", "Brand-New-Pass-2026", "Brand-New-Pass-2026"));

        verify(u).setPasswordHash("newhash");
        verify(u).setMustChangePassword(false);
        verify(u).setTokenValidAfter(any(LocalDateTime.class));
        verify(refreshTokenRepository).revokeAllForUser(5L);
    }

    @Test
    void changePasswordRejectsReuseOfCurrentAndWeakPasswords() {
        user(RecordStatus.ACTIVE);
        signedIn();
        when(passwordEncoder.matches(anyString(), eq("hash"))).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.changePassword(changeReq("Same-Passw0rd-1", "Same-Passw0rd-1", "Same-Passw0rd-1")));
        assertThrows(BusinessException.class, () -> service.changePassword(changeReq("Old-Passw0rd-1", "short1", "short1")));
        assertThrows(BusinessException.class, () -> service.changePassword(changeReq("Old-Passw0rd-1", "Password12345", "Password12345")));
        assertThrows(BusinessException.class, () -> service.changePassword(changeReq("Old-Passw0rd-1", "bob-bob-bob-2026", "bob-bob-bob-2026")));
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }
}
