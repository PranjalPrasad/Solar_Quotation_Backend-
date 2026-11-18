package com.solar.Quotation.service.serviceImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.dto.requestdto.QuotationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationHistoryDto;
import com.solar.Quotation.dto.requestdto.QuotationRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationStatusRequestDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.entity.QuotationHistory;
import com.solar.Quotation.entity.QuotationItem;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.*;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.ApprovalGuard;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.QuotationHistoryService;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Quotation tenant rules against in-memory stores that filter like the real queries (service, TenantResolver,
 * CurrentUser and QuotationHistoryService are the real classes).
 *   PLATFORM (1) = Super Admin's tenant, A (12) and B (13) = partners.
 *   - quotation, items and history of another partner are a 404 on every endpoint, nothing is written or audited;
 *   - Super Admin sees a partner only through ?viewPartnerId= on a GET, and that view is read only;
 *   - items are only ever queried with the partner of a tenant-checked quotation;
 *   - history reads filter by channel_partner_id;
 *   - delete / approve / reject / convert are audited with previous and new data, status changes log at INFO;
 *   - approve / reject need APPROVAL:EDIT, never reach another partner's quotation (also not for a Super Admin who is
 *     only viewing it), refuse self-approval, and log "[APPROVAL]" at INFO with the quotation id and the actor.
 *   Roles: 10 = holds APPROVAL:EDIT + QUOTATION:EDIT, 11 = QUOTATION:EDIT only (e.g. Partner Sales).
 *   Users: 5 = creator of every seeded quotation, 6 / 7 = other users (approvers).
 */
class QuotationTenantVisibilityTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;

    private final List<Quotation> quotations = new ArrayList<>();
    private final List<QuotationItem> items = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final List<QuotationHistory> historyRows = new ArrayList<>();
    private long nextId = 100;

    private QuotationRepository quotationRepository;
    private QuotationItemRepository itemRepository;
    private QuotationHistoryRepository historyRepository;
    private AuditService auditService;
    private QuotationServiceImpl service;
    private QuotationHistoryService historyService;
    private PermissionCache permissionCache;

    @BeforeEach
    void setUp() {
        quotationRepository = mock(QuotationRepository.class);
        itemRepository = mock(QuotationItemRepository.class);
        historyRepository = mock(QuotationHistoryRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        auditService = mock(AuditService.class);
        CodeGenerator codeGenerator = mock(CodeGenerator.class);
        when(codeGenerator.next(any(), any())).thenReturn("PRJ-0001");
        SettingsProvider settings = mock(SettingsProvider.class);
        SavingsConfigResponseDto cfg = mock(SavingsConfigResponseDto.class);
        when(cfg.getUnitsPerKwPerDay()).thenReturn(new BigDecimal("4"));
        when(cfg.getDegradationPercent()).thenReturn(new BigDecimal("0.5"));
        when(cfg.getCo2FactorKgPerKwh()).thenReturn(new BigDecimal("0.82"));
        when(cfg.getProjectionYears()).thenReturn(25);
        when(cfg.getMarketRatePerKw()).thenReturn(new BigDecimal("60000"));
        when(settings.savingsConfig(any())).thenReturn(cfg);

        when(quotationRepository.findOne(any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return quotations.stream().filter(q -> q.getId().equals(id) && visible(q, inv.getArgument(1), inv.getArgument(2))).findFirst();
        });
        when(quotationRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv ->
                new PageImpl<>(quotations.stream().filter(q -> visible(q, inv.getArgument(0), inv.getArgument(1))).toList()));
        when(quotationRepository.countFiltered(any(), any(), any())).thenAnswer(inv ->
                quotations.stream().filter(q -> visible(q, inv.getArgument(0), inv.getArgument(1))).count());
        when(quotationRepository.sumTotal(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findForQuotation(any(), any())).thenAnswer(inv -> items.stream()
                .filter(i -> i.getQuotationId().equals(inv.getArgument(0)) && i.getChannelPartnerId().equals(inv.getArgument(1))).toList());
        when(customerRepository.findById(any())).thenAnswer(inv ->
                customers.stream().filter(c -> c.getId().equals(inv.getArgument(0))).findFirst());
        when(customerRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<Customer> out = new ArrayList<>();
            ids.forEach(i -> customers.stream().filter(c -> c.getId().equals(i)).forEach(out::add));
            return out;
        });
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(nextId++);
            return p;
        });

        when(historyRepository.save(any(QuotationHistory.class))).thenAnswer(inv -> {
            QuotationHistory h = inv.getArgument(0);
            historyRows.add(h);
            return h;
        });
        when(historyRepository.findByQuotationIdAndChannelPartnerIdOrderByVersionDesc(any(), any())).thenAnswer(inv ->
                historyRows.stream().filter(h -> h.getQuotationId().equals(inv.getArgument(0))
                        && h.getChannelPartnerId().equals(inv.getArgument(1))).toList());
        when(historyRepository.maxVersion(any(), any())).thenAnswer(inv -> historyRows.stream()
                .filter(h -> h.getQuotationId().equals(inv.getArgument(0)) && h.getChannelPartnerId().equals(inv.getArgument(1)))
                .mapToInt(QuotationHistory::getVersion).max().orElse(0));
        historyService = new QuotationHistoryService(historyRepository, JsonMapper.builder().build());

        permissionCache = mock(PermissionCache.class);
        when(permissionCache.permissionsForRole(10L)).thenReturn(Set.of("QUOTATION:EDIT", "APPROVAL:EDIT"));
        when(permissionCache.permissionsForRole(11L)).thenReturn(Set.of("QUOTATION:EDIT"));

        customerRepositoryRef = customerRepository;
        projectRepositoryRef = projectRepository;
        codeGeneratorRef = codeGenerator;
        settingsRef = settings;
        service = serviceWithSelfApproval(false);
    }

    private CustomerRepository customerRepositoryRef;
    private ProjectRepository projectRepositoryRef;
    private CodeGenerator codeGeneratorRef;
    private SettingsProvider settingsRef;

    private QuotationServiceImpl serviceWithSelfApproval(boolean allowed) {
        return new QuotationServiceImpl(quotationRepository, itemRepository, customerRepositoryRef, projectRepositoryRef,
                mock(ProductRepository.class), new TenantResolver(), mock(PartnerRefProvider.class),
                mock(AuditInfoMapper.class), auditService, codeGeneratorRef, settingsRef, historyService,
                new ApprovalGuard(permissionCache, allowed));
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean visible(Quotation q, Long partner, Long owner) {
        return !q.isDeleted() && (partner == null || partner.equals(q.getChannelPartnerId()))
                && (owner == null || owner.equals(q.getCreatedBy()));
    }

    private void login(DataScope scope, Long partnerId, Long userId) {
        String role = scope == DataScope.ALL ? "SUPER_ADMIN" : "ROLE_X";
        TenantContext.set(new CurrentUser(userId, "Tester", 10L, role, scope, partnerId));
    }

    private void loginWithRole(DataScope scope, Long partnerId, Long userId, Long roleId) {
        TenantContext.set(new CurrentUser(userId, "Tester", roleId, "ROLE_X", scope, partnerId));
    }

    private void viewing(Long viewPartnerId) {          // what JwtAuthFilter does for a GET with ?viewPartnerId=
        CurrentUser u = TenantContext.get();
        u.setReadRequest(true);
        u.setViewPartnerId(viewPartnerId);
    }

    private Customer seedCustomer(Long partner) {
        Customer c = new Customer();
        c.setId(nextId++);
        c.setChannelPartnerId(partner);
        c.setName("Asha");
        customers.add(c);
        return c;
    }

    private Quotation seed(Long partner, QuotationStatus status) {
        Customer c = seedCustomer(partner);
        Quotation q = new Quotation();
        q.setId(nextId++);
        q.setChannelPartnerId(partner);
        q.setCreatedBy(5L);
        q.setCode("SQ-" + q.getId());
        q.setCustomerId(c.getId());
        q.setQuoteDate(LocalDate.of(2026, 11, 1));
        q.setValidUntil(LocalDate.of(2026, 11, 30));
        q.setStatus(status);
        q.setSystemSizeKw(new BigDecimal("3.00"));
        q.setTotalAmount(new BigDecimal("150000.00"));
        q.setNetInvestment(new BigDecimal("72000.00"));
        quotations.add(q);
        QuotationItem it = new QuotationItem();
        it.setId(nextId++);
        it.setQuotationId(q.getId());
        it.setChannelPartnerId(partner);
        it.setName("Panel");
        it.setQty(new BigDecimal("6"));
        it.setUnit("Nos");
        it.setRate(new BigDecimal("20000"));
        it.setAmount(new BigDecimal("120000"));
        items.add(it);
        return q;
    }

    private void seedHistory(Long partner, Long quotationId, int version, String role) {
        QuotationHistory h = new QuotationHistory();
        h.setChannelPartnerId(partner);
        h.setQuotationId(quotationId);
        h.setQuotationCode("SQ");
        h.setVersion(version);
        h.setAction("UPDATED");
        h.setChangedByRole(role);
        historyRows.add(h);
    }

    private static QuotationStatusRequestDto status(String s) {
        QuotationStatusRequestDto r = new QuotationStatusRequestDto();
        r.setStatus(s);
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> audited(AuditAction action, Long partnerId) {
        ArgumentCaptor<List<Map<String, String>>> c = ArgumentCaptor.forClass(List.class);
        verify(auditService).record(eq(action), eq("QUOTATION"), eq("Quotation"), any(), any(), eq(partnerId), c.capture());
        return c.getValue();
    }

    // ------------------------------------------------------------------ cross-partner by id

    @Test
    void partnerB_cannotReadPartnerAsQuotation_itemsOrHistory() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        seedHistory(A, a.getId(), 1, "ROLE_X");
        login(DataScope.PARTNER, B, 7L);

        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.history(a.getId()));
        assertTrue(service.list(new QuotationFilterRequestDto()).getContent().isEmpty());
        assertEquals(0L, service.summary(null).getTotal());
        // the items of A were never even queried: they are only reachable through a checked quotation
        verify(itemRepository, never()).findForQuotation(any(), any());
        verify(historyRepository, never()).findByQuotationIdAndChannelPartnerIdOrderByVersionDesc(any(), any());
    }

    @Test
    void partnerB_cannotChangePartnerAsQuotation_noWriteNoAuditNoHistory() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        login(DataScope.PARTNER, B, 7L);

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), new QuotationRequestDto()));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));

        assertEquals(QuotationStatus.PENDING, a.getStatus());
        assertFalse(a.isDeleted());
        verify(quotationRepository, never()).save(any());
        verify(itemRepository, never()).deleteForQuotation(any(), any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void partnerB_summaryIgnoresAClientPartnerId() {
        seed(A, QuotationStatus.PENDING);
        login(DataScope.PARTNER, B, 7L);
        assertEquals(0L, service.summary(A).getTotal());
        QuotationFilterRequestDto f = new QuotationFilterRequestDto();
        f.setPartnerId(A);
        assertTrue(service.list(f).getContent().isEmpty());
    }

    // ------------------------------------------------------------------ Super Admin: own tenant and read-only view

    @Test
    void superAdminOwnView_doesNotSeePartnerData_andCannotWriteIt() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        login(DataScope.ALL, PLATFORM, 1L);

        assertTrue(service.list(new QuotationFilterRequestDto()).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("REJECTED")));
    }

    @Test
    void superAdminViewingA_readsQuotationItemsAndHistory_butEveryWriteIsRefused() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        seedHistory(A, a.getId(), 1, "ROLE_X");
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);

        assertEquals(1, service.list(new QuotationFilterRequestDto()).getContent().size());
        assertEquals(a.getId(), service.get(a.getId()).getId());
        assertEquals(1, service.get(a.getId()).getItems().size());
        verify(itemRepository, atLeastOnce()).findForQuotation(a.getId(), A);     // A's partner id, never PLATFORM
        assertEquals(1, service.history(a.getId()).size());

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), new QuotationRequestDto()));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        verify(quotationRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void partnerUser_cannotUseViewPartnerId() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        seed(B, QuotationStatus.PENDING);
        login(DataScope.PARTNER, B, 7L);
        viewing(A);

        assertEquals(1, service.list(new QuotationFilterRequestDto()).getContent().size());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
    }

    // ------------------------------------------------------------------ history is filtered by partner

    @Test
    void historyRead_filtersByChannelPartnerId_evenWhenAnotherPartnersRowSharesTheQuotationId() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        seedHistory(A, a.getId(), 1, "ROLE_X");
        seedHistory(B, a.getId(), 2, "ROLE_X");           // must never be shown under A
        login(DataScope.PARTNER, A, 5L);

        List<QuotationHistoryDto> rows = service.history(a.getId());
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).version());
        assertTrue(historyService.list(a.getId(), B).stream().allMatch(h -> h.version() == 2));
        assertTrue(historyService.list(a.getId(), null).isEmpty());
    }

    @Test
    void historyIsWrittenWithThePartnerOfTheQuotation() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        login(DataScope.PARTNER, A, 6L);   // approver, not the creator (user 5)
        service.updateStatus(a.getId(), status("REJECTED"));
        assertFalse(historyRows.isEmpty());
        assertTrue(historyRows.stream().allMatch(h -> A.equals(h.getChannelPartnerId())));
    }

    // ------------------------------------------------------------------ audit

    @Test
    void delete_isAuditedWithPreviousData_andRecordedInHistory() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        login(DataScope.PARTNER, A, 5L);

        service.delete(a.getId());

        assertTrue(a.isDeleted());
        List<Map<String, String>> changes = audited(AuditAction.DELETE, A);
        Map<String, String> status = changes.stream().filter(m -> "status".equals(m.get("field"))).findFirst().orElseThrow();
        assertEquals("PENDING", status.get("old"));
        assertNull(status.get("new"));
        assertTrue(historyRows.stream().anyMatch(h -> "DELETED".equals(h.getAction()) && A.equals(h.getChannelPartnerId())));
    }

    @Test
    void accept_isAuditedAsApprove_andConvertWithPreviousAndNewValues() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        login(DataScope.PARTNER, A, 6L);   // approver, not the creator (user 5)

        service.updateStatus(a.getId(), status("ACCEPTED"));

        List<Map<String, String>> approve = audited(AuditAction.APPROVE, A);
        Map<String, String> st = approve.stream().filter(m -> "status".equals(m.get("field"))).findFirst().orElseThrow();
        assertEquals("PENDING", st.get("old"));
        assertEquals("ACCEPTED", st.get("new"));
        List<Map<String, String>> convert = audited(AuditAction.CONVERT, A);
        assertNull(convert.get(0).get("old"));
        assertEquals("PRJ-0001", convert.get(0).get("new"));
        assertTrue(historyRows.stream().anyMatch(h -> "CONVERTED".equals(h.getAction())));
        verify(auditService, never()).record(eq(AuditAction.STATUS_CHANGE), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reject_isAuditedAsReject() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        login(DataScope.PARTNER, A, 6L);   // approver, not the creator (user 5)
        service.updateStatus(a.getId(), status("REJECTED"));
        assertEquals("REJECTED", audited(AuditAction.REJECT, A).stream()
                .filter(m -> "status".equals(m.get("field"))).findFirst().orElseThrow().get("new"));
    }

    @Test
    void sameStatus_writesNothing() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        login(DataScope.PARTNER, A, 6L);   // approver, not the creator (user 5)
        service.updateStatus(a.getId(), status("PENDING"));
        verify(quotationRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }


    // ------------------------------------------------------------------ approval rules

    private static ch.qos.logback.classic.Logger loggerOf(Class<?> c) {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(c);
    }

    private void assertNothingWritten(Quotation q, QuotationStatus expectedStatus) {
        assertEquals(expectedStatus, q.getStatus());
        verify(quotationRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void crossPartner_partnerBApprover_cannotApproveOrRejectPartnerAsQuotation() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        loginWithRole(DataScope.PARTNER, B, 7L, 10L);              // B holds the approval permission

        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("REJECTED")));

        assertNothingWritten(a, QuotationStatus.PENDING);
        assertNull(a.getProjectId());                               // an approve would also have opened a project
        verify(auditService, never()).record(eq(AuditAction.APPROVE), any(), any(), any(), any(), any(), any());
        verify(auditService, never()).record(eq(AuditAction.REJECT), any(), any(), any(), any(), any(), any());
        verify(itemRepository, never()).findForQuotation(any(), any());
    }

    @Test
    void crossPartner_attemptIsLoggedAtWarn_withIdsOnly() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Logger logger = loggerOf(TenantResolver.class);
        logger.addAppender(appender);
        try {
            Quotation a = seed(A, QuotationStatus.PENDING);
            loginWithRole(DataScope.PARTNER, B, 7L, 10L);
            assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));

            ILoggingEvent e = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[TENANT] cross-tenant write blocked")).findFirst().orElseThrow();
            assertEquals(Level.WARN, e.getLevel());
            assertTrue(e.getFormattedMessage().contains("userId=7"));
            assertTrue(e.getFormattedMessage().contains("id=" + a.getId()));
            assertFalse(e.getFormattedMessage().contains("Asha"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void crossPartner_partnerAApprover_cannotTouchPartnerBsQuotationEither() {
        Quotation b = seed(B, QuotationStatus.PENDING);
        loginWithRole(DataScope.PARTNER, A, 6L, 10L);
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(b.getId(), status("REJECTED")));
        assertNothingWritten(b, QuotationStatus.PENDING);
    }

    @Test
    void superAdminViewingPartnerA_isReadOnly_approveAndRejectAreRefused() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        loginWithRole(DataScope.ALL, PLATFORM, 1L, 10L);
        viewing(A);

        assertEquals(a.getId(), service.get(a.getId()).getId());                    // the view itself works
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("REJECTED")));
        assertNothingWritten(a, QuotationStatus.PENDING);
    }

    @Test
    void withoutApprovalPermission_sameTenant_isForbidden_andNothingIsLoadedOrWritten() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        loginWithRole(DataScope.PARTNER, A, 6L, 11L);               // QUOTATION:EDIT only

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));
        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertThrows(ForbiddenException.class, () -> service.updateStatus(a.getId(), status("REJECTED")));

        verify(quotationRepository, never()).findOne(any(), any(), any());
        assertNothingWritten(a, QuotationStatus.PENDING);
    }

    @Test
    void withoutApprovalPermission_isLoggedAtWarn() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Logger logger = loggerOf(ApprovalGuard.class);
        logger.addAppender(appender);
        try {
            Quotation a = seed(A, QuotationStatus.PENDING);
            loginWithRole(DataScope.PARTNER, A, 6L, 11L);
            assertThrows(ForbiddenException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));

            ILoggingEvent e = appender.list.get(0);
            assertEquals(Level.WARN, e.getLevel());
            assertTrue(e.getFormattedMessage().startsWith("[APPROVAL] denied"));
            assertTrue(e.getFormattedMessage().contains("quotationId=" + a.getId()));
            assertTrue(e.getFormattedMessage().contains("userId=6"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void selfApproval_isRefused_forApproveAndReject_evenWithThePermission() {
        Quotation a = seed(A, QuotationStatus.PENDING);              // created by user 5
        loginWithRole(DataScope.PARTNER, A, 5L, 10L);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED")));
        assertEquals("SELF_APPROVAL", ex.getErrorCode());
        assertEquals("SELF_APPROVAL",
                assertThrows(ForbiddenException.class, () -> service.updateStatus(a.getId(), status("REJECTED"))).getErrorCode());
        assertNothingWritten(a, QuotationStatus.PENDING);
        assertNull(a.getProjectId());
    }

    @Test
    void selfApproval_appliesToSuperAdminToo() {
        Quotation a = seed(PLATFORM, QuotationStatus.PENDING);
        a.setCreatedBy(1L);
        loginWithRole(DataScope.ALL, PLATFORM, 1L, 10L);
        assertEquals("SELF_APPROVAL",
                assertThrows(ForbiddenException.class, () -> service.updateStatus(a.getId(), status("ACCEPTED"))).getErrorCode());
        assertNothingWritten(a, QuotationStatus.PENDING);
    }

    @Test
    void selfApproval_canBeSwitchedOn_forSingleUserPartners() {
        QuotationServiceImpl lenient = serviceWithSelfApproval(true);
        Quotation a = seed(A, QuotationStatus.PENDING);
        loginWithRole(DataScope.PARTNER, A, 5L, 10L);
        lenient.updateStatus(a.getId(), status("REJECTED"));
        assertEquals(QuotationStatus.REJECTED, a.getStatus());
    }

    @Test
    void anotherUserOfTheSamePartner_withPermission_canApprove() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        loginWithRole(DataScope.PARTNER, A, 6L, 10L);
        service.updateStatus(a.getId(), status("ACCEPTED"));
        assertEquals(QuotationStatus.ACCEPTED, a.getStatus());
        assertNotNull(a.getProjectId());
    }

    @Test
    void paidOrExpiredQuotation_cannotBeReviewed() {
        Quotation paid = seed(A, QuotationStatus.PAID);
        Quotation expired = seed(A, QuotationStatus.EXPIRED);
        loginWithRole(DataScope.PARTNER, A, 6L, 10L);
        assertThrows(BusinessException.class, () -> service.updateStatus(paid.getId(), status("REJECTED")));
        assertThrows(BusinessException.class, () -> service.updateStatus(expired.getId(), status("ACCEPTED")));
        assertEquals(QuotationStatus.PAID, paid.getStatus());
        assertEquals(QuotationStatus.EXPIRED, expired.getStatus());
        verify(quotationRepository, never()).save(any());
    }

    @Test
    void reject_auditRowCarriesPreviousAndNewStatus_underThePartnersId() {
        Quotation a = seed(A, QuotationStatus.PENDING);
        loginWithRole(DataScope.PARTNER, A, 6L, 10L);
        service.updateStatus(a.getId(), status("REJECTED"));

        Map<String, String> st = audited(AuditAction.REJECT, A).stream()
                .filter(m -> "status".equals(m.get("field"))).findFirst().orElseThrow();
        assertEquals("PENDING", st.get("old"));
        assertEquals("REJECTED", st.get("new"));
        verify(auditService, never()).record(eq(AuditAction.APPROVE), any(), any(), any(), any(), any(), any());
    }

    @Test
    void decision_isLoggedAtInfo_withQuotationIdAndActor() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Logger logger = loggerOf(QuotationServiceImpl.class);
        logger.addAppender(appender);
        try {
            Quotation a = seed(A, QuotationStatus.PENDING);
            loginWithRole(DataScope.PARTNER, A, 6L, 10L);
            service.updateStatus(a.getId(), status("ACCEPTED"));

            ILoggingEvent e = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[APPROVAL]")).findFirst().orElseThrow();
            assertEquals(Level.INFO, e.getLevel());
            String m = e.getFormattedMessage();
            assertTrue(m.contains("approved"));
            assertTrue(m.contains("quotationId=" + a.getId()));
            assertTrue(m.contains("userId=6"));
            assertTrue(m.contains("PENDING -> ACCEPTED"));
            assertFalse(m.contains("Asha"));
            assertFalse(m.contains("Tester"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void rejection_isLoggedAsRejected_andBackToPending_isNotADecision() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Logger logger = loggerOf(QuotationServiceImpl.class);
        logger.addAppender(appender);
        try {
            Quotation a = seed(A, QuotationStatus.PENDING);
            loginWithRole(DataScope.PARTNER, A, 6L, 10L);
            service.updateStatus(a.getId(), status("REJECTED"));
            service.updateStatus(a.getId(), status("PENDING"));

            long decisions = appender.list.stream().filter(x -> x.getFormattedMessage().startsWith("[APPROVAL]")).count();
            assertEquals(1, decisions);
            assertTrue(appender.list.stream().anyMatch(x -> x.getFormattedMessage().startsWith("[APPROVAL] rejected")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ------------------------------------------------------------------ logging

    @Test
    void statusTransition_isLoggedAtInfo_withIdsOnly() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(QuotationServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Quotation a = seed(A, QuotationStatus.PENDING);
            login(DataScope.PARTNER, A, 6L);   // approver, not the creator (user 5)
            service.updateStatus(a.getId(), status("REJECTED"));

            ILoggingEvent e = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[QUOTATION] status")).findFirst().orElseThrow();
            assertEquals(Level.INFO, e.getLevel());
            assertTrue(e.getFormattedMessage().contains("PENDING -> REJECTED"));
            assertFalse(e.getFormattedMessage().contains("Asha"));          // no customer name in the log
        } finally {
            logger.detachAppender(appender);
        }
    }
}