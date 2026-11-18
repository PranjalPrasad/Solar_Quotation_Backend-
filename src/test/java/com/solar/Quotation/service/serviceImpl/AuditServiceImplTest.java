package com.solar.Quotation.service.serviceImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.dto.requestdto.AuditFilterRequestDto;
import com.solar.Quotation.entity.AuditLog;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.AuditLogRepository;
import com.solar.Quotation.security.AuditAccessGuard;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.PlatformTenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceImplTest {

    private static final Long PLATFORM = 1L;
    private static final Long PARTNER = 12L;
    private static final String SECRET = "SuperSecret123";

    private AuditLogRepository repo;
    private PermissionCache permissions;
    private AuditServiceImpl service;
    private ListAppender<ILoggingEvent> logs;
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditServiceImpl.class);

    @BeforeEach
    void setUp() {
        repo = mock(AuditLogRepository.class);
        permissions = mock(PermissionCache.class);
        PlatformTenant platform = mock(PlatformTenant.class);
        when(platform.id()).thenReturn(PLATFORM);
        AuditAccessGuard guard = new AuditAccessGuard(permissions, platform);
        service = new AuditServiceImpl(repo, mock(TenantResolver.class), mock(PartnerRefProvider.class), platform, guard,
                JsonMapper.builder().build(), mock(PlatformTransactionManager.class));
        when(repo.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<AuditLog>(List.of()));
        logs = new ListAppender<>();
        logs.start();
        auditLogger.addAppender(logs);
        auditLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(logs);
        TenantContext.clear();
    }

    private void loginAs(Long partnerId, Long roleId, String... permissionCodes) {
        TenantContext.set(new CurrentUser(7L, "Tester", roleId, "ROLE_X", DataScope.PARTNER, partnerId));
        when(permissions.permissionsForRole(roleId)).thenReturn(Set.of(permissionCodes));
    }

    private String logText() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    private static Map<String, Object> snapshot(String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Asha");
        m.put("password", password);
        return m;
    }

    // ------------------------------------------------------------------ writing

    @Test
    void recordStoresMaskedSnapshotsAndBothTenantIds() {
        loginAs(PARTNER, 5L);
        service.record(AuditAction.UPDATE, "USER", "AppUser", 9L, "U-9", PARTNER, snapshot("old-" + SECRET), snapshot("new-" + SECRET));
        ArgumentCaptor<AuditLog> c = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(c.capture());
        AuditLog row = c.getValue();
        assertEquals(PARTNER, row.getTenantId());
        assertEquals(PARTNER, row.getActorTenantId());
        assertEquals(7L, row.getActorUserId());
        assertTrue(row.getPreviousData().contains("[REDACTED]"));
        assertTrue(row.getNewData().contains("[REDACTED]"));
        assertFalse(row.getPreviousData().contains(SECRET));
        assertFalse(row.getNewData().contains(SECRET));
        assertFalse(row.getChangesJson().contains(SECRET));
        assertTrue(row.getPreviousData().contains("Asha"));
    }

    @Test
    void createHasNoPreviousDataAndDeleteHasNoNewData() {
        loginAs(PARTNER, 5L);
        service.record(AuditAction.CREATE, "CUSTOMER", "Customer", 1L, "C-1", PARTNER, null, snapshot("x"));
        service.record(AuditAction.DELETE, "CUSTOMER", "Customer", 1L, "C-1", PARTNER, snapshot("x"), null);
        ArgumentCaptor<AuditLog> c = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo, org.mockito.Mockito.times(2)).save(c.capture());
        assertNull(c.getAllValues().get(0).getPreviousData());
        assertNull(c.getAllValues().get(1).getNewData());
    }

    @Test
    void aMissingTenantFallsBackToPlatform() {
        service.recordEvent(null, AuditAction.TOKEN_REUSE_DETECTED, "AUTH", "AppUser", 3L, "u", null, "x");
        ArgumentCaptor<AuditLog> c = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(c.capture());
        assertEquals(PLATFORM, c.getValue().getTenantId());
        assertNull(c.getValue().getActorTenantId());
    }

    @Test
    void recordJoinsTheCallersTransactionAndFailsWithoutOne() throws Exception {
        Transactional t = AuditServiceImpl.class.getMethod("record", AuditAction.class, String.class, String.class,
                Long.class, String.class, Long.class, Map.class, Map.class).getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, t.propagation());
        Transactional t2 = AuditServiceImpl.class.getMethod("recordAs", CurrentUser.class, AuditAction.class, String.class,
                String.class, Long.class, String.class, Long.class, Map.class, Map.class).getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, t2.propagation());
    }

    @Test
    void whenTheAuditRowCannotBeWrittenTheRequestFailsAndNothingSensitiveIsLogged() {
        loginAs(PARTNER, 5L);
        when(repo.save(any())).thenThrow(new RuntimeException("insert failed near " + SECRET));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.record(AuditAction.UPDATE, "USER", "AppUser", 9L, "U-9", PARTNER, snapshot(SECRET), snapshot(SECRET)));
        assertEquals("AUDIT_WRITE_FAILED", ex.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        ILoggingEvent err = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertTrue(err.getFormattedMessage().startsWith("[AUDIT]"));
        assertTrue(err.getFormattedMessage().contains("fail-closed"));
        assertFalse(logText().contains(SECRET), "snapshot / exception text leaked into the log");
        assertFalse(logText().contains("Asha"), "snapshot leaked into the log");
        assertNull(err.getThrowableProxy(), "exception (its message can quote data) must not be attached at ERROR");
    }

    @Test
    void anEventThatCannotBeWrittenIsLoggedAtErrorAndSwallowed() {
        when(repo.save(any())).thenThrow(new RuntimeException("db down " + SECRET));
        service.recordEvent(null, AuditAction.LOGIN_FAILED, "AUTH", "AppUser", 3L, "u", PARTNER, "ip=1.2.3.4");
        ILoggingEvent err = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertTrue(err.getFormattedMessage().startsWith("[AUDIT]"));
        assertTrue(err.getFormattedMessage().contains("alert-only"));
        assertFalse(logText().contains(SECRET));
    }

    // ------------------------------------------------------------------ access

    @Test
    void partnerUsersGet403EvenWithThePermission() {
        loginAs(PARTNER, 5L, "AUDIT:VIEW", "AUDIT:EXPORT");
        assertThrows(ForbiddenException.class, () -> service.search(new AuditFilterRequestDto()));
        assertThrows(ForbiddenException.class, () -> service.get(1L));
        assertThrows(ForbiddenException.class, () -> service.exportCsv(new AuditFilterRequestDto()));
        assertThrows(ForbiddenException.class, () -> service.filterOptions());
        verify(repo, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void platformStaffWithoutThePermissionGet403() {
        loginAs(PLATFORM, 6L, "DASHBOARD:VIEW");
        assertThrows(ForbiddenException.class, () -> service.search(new AuditFilterRequestDto()));
        verify(repo, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void viewDoesNotAllowExport() {
        loginAs(PLATFORM, 6L, "AUDIT:VIEW");
        service.search(new AuditFilterRequestDto());
        assertThrows(ForbiddenException.class, () -> service.exportCsv(new AuditFilterRequestDto()));
    }

    @Test
    void nobodyLoggedInGets403() {
        assertThrows(ForbiddenException.class, () -> service.search(new AuditFilterRequestDto()));
    }

    // ------------------------------------------------------------------ filters

    private Long tenantFilterFor(AuditFilterRequestDto f) {
        service.search(f);
        ArgumentCaptor<Long> tenant = ArgumentCaptor.forClass(Long.class);
        verify(repo).search(tenant.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        return tenant.getValue();
    }

    @Test
    void partnerPlatformMapsToThePlatformTenant() {
        loginAs(PLATFORM, 6L, "AUDIT:VIEW");
        AuditFilterRequestDto f = new AuditFilterRequestDto();
        f.setPartner("platform");
        assertEquals(PLATFORM, tenantFilterFor(f));
    }

    @Test
    void partnerIdFilterAndNoFilter() {
        loginAs(PLATFORM, 6L, "AUDIT:VIEW");
        AuditFilterRequestDto f = new AuditFilterRequestDto();
        f.setPartnerId(PARTNER);
        assertEquals(PARTNER, tenantFilterFor(f));
    }

    @Test
    void noPartnerFilterMeansAllTenants() {
        loginAs(PLATFORM, 6L, "AUDIT:VIEW");
        assertNull(tenantFilterFor(new AuditFilterRequestDto()));
    }

    @Test
    void badFiltersAreRejected() {
        loginAs(PLATFORM, 6L, "AUDIT:VIEW");
        AuditFilterRequestDto f = new AuditFilterRequestDto();
        f.setPartner("abc");
        assertThrows(BusinessException.class, () -> service.search(f));
        AuditFilterRequestDto g = new AuditFilterRequestDto();
        g.setAction("NOPE");
        assertThrows(BusinessException.class, () -> service.search(g));
        AuditFilterRequestDto h = new AuditFilterRequestDto();
        h.setFrom(java.time.LocalDate.of(2026, 5, 2));
        h.setTo(java.time.LocalDate.of(2026, 5, 1));
        assertThrows(BusinessException.class, () -> service.search(h));
    }

    @Test
    void exportWritesAnExportAuditRowAndNeverTheData() {
        loginAs(PLATFORM, 6L, "AUDIT:VIEW", "AUDIT:EXPORT");
        byte[] csv = service.exportCsv(new AuditFilterRequestDto());
        assertTrue(new String(csv, java.nio.charset.StandardCharsets.UTF_8).startsWith("Time (UTC),Tenant ID,Partner"));
        ArgumentCaptor<AuditLog> c = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(c.capture());
        assertEquals(AuditAction.EXPORT, c.getValue().getAction());
        assertEquals("AUDIT", c.getValue().getModule());
    }

}