package com.solar.Quotation.service.serviceImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.dto.responsedto.AdminDashboardResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardRecentQuotationResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardScopeResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardSummaryResponseDto;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.DashboardQueryRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.SuperAdminGuard;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.PartnerRefProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Dashboard tenant rules, with per-tenant data behind a mocked repository (the service, TenantResolver and CurrentUser
 * are the real classes). PLATFORM (1) = Super Admin's own tenant, A (12) and B (13) = partners.
 *
 *   - a tenant dashboard only ever queries ONE partner id: the caller's own, or the viewed one for a Super Admin GET
 *   - the default Super Admin dashboard is the PLATFORM tenant's own data, never the all-partners totals
 *   - cross-partner totals only come out of adminSummary(), which refuses everyone but Super Admin before any query
 *   - [DASHBOARD] logging: slow queries at WARN, failures at ERROR with the stack
 */
class DashboardServiceImplTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), DashboardServiceImpl.KOLKATA);

    private DashboardQueryRepository queries;
    private PartnerRefProvider partnerRefs;
    private SuperAdminGuard guard;
    private final List<Long> queriedPartners = new ArrayList<>();
    private final List<Long> queriedOwners = new ArrayList<>();
    private final List<LocalDate> periodArgs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        queries = mock(DashboardQueryRepository.class);
        partnerRefs = mock(PartnerRefProvider.class);
        guard = mock(SuperAdminGuard.class);

        PartnerRefResponseDto refA = new PartnerRefResponseDto();
        refA.setId(A);
        refA.setName("Alpha Solar");
        refA.setCode("CP-A");
        when(partnerRefs.ref(A)).thenReturn(refA);

        // tenant A: 5 quotations (2 pending, 1 accepted, 1 rejected, 1 approved); tenant B: 1 pending; PLATFORM: none
        when(queries.statusTotals(any(), any())).thenAnswer(inv -> {
            record(inv.getArgument(0), inv.getArgument(1));
            Long p = inv.getArgument(0);
            if (A.equals(p)) {
                return List.of(row("PENDING", 2L, "200000"), row("ACCEPTED", 1L, "100000"),
                        row("REJECTED", 1L, "50000"), row("APPROVED", 1L, "150000"));
            }
            if (B.equals(p)) return List.of(row("PENDING", 1L, "10000"));
            return List.of();
        });
        when(queries.periodTotals(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            record(inv.getArgument(0), inv.getArgument(1));
            periodArgs.add(inv.getArgument(2));
            periodArgs.add(inv.getArgument(3));
            periodArgs.add(inv.getArgument(4));
            Long p = inv.getArgument(0);
            return A.equals(p) ? List.<Object[]>of(new Object[]{1L, 3L, new BigDecimal("300000")})
                    : List.<Object[]>of(new Object[]{0L, 0L, BigDecimal.ZERO});
        });
        when(queries.sizeBuckets(any(), any(), any(), any())).thenAnswer(inv -> {
            record(inv.getArgument(0), inv.getArgument(1));
            Long p = inv.getArgument(0);
            return A.equals(p) ? List.<Object[]>of(new Object[]{2L, 2L, 1L}) : List.<Object[]>of(new Object[]{0L, 0L, 0L});
        });
        when(queries.countCustomers(any(), any())).thenAnswer(inv -> {
            record(inv.getArgument(0), inv.getArgument(1));
            return A.equals(inv.getArgument(0)) ? 7L : 1L;
        });
        when(queries.countProjects(any(), any())).thenAnswer(inv -> {
            record(inv.getArgument(0), inv.getArgument(1));
            return A.equals(inv.getArgument(0)) ? 4L : 0L;
        });
        when(queries.referralTotalsAsReferrer(any())).thenAnswer(inv -> {
            queriedPartners.add(inv.getArgument(0));
            return A.equals(inv.getArgument(0))
                    ? List.of(row("APPROVED", null, "1000"), row("PAID", null, "500"), row("PENDING", null, "99999"))
                    : List.<Object[]>of();
        });
        when(queries.recent(any(), any(), any())).thenAnswer(inv -> {
            record(inv.getArgument(0), inv.getArgument(1));
            Long p = inv.getArgument(0);
            return A.equals(p)
                    ? List.<Object[]>of(
                    new Object[]{"SQ-1", "Ravi", new BigDecimal("5"), new BigDecimal("1000"), "PENDING", LocalDate.of(2026, 11, 10)},
                    new Object[]{"SQ-2", null, new BigDecimal("8"), new BigDecimal("2000"), "ACCEPTED", LocalDate.of(2026, 11, 9)})
                    : List.<Object[]>of();
        });
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private DashboardServiceImpl service(long slowMs) {
        return new DashboardServiceImpl(queries, new TenantResolver(), partnerRefs, guard, slowMs, CLOCK);
    }

    private DashboardServiceImpl service() {
        return service(1000);
    }

    private static Object[] row(String status, Long count, String value) {
        return count == null ? new Object[]{status, new BigDecimal(value)} : new Object[]{status, count, new BigDecimal(value)};
    }

    private void record(Long partner, Long owner) {
        queriedPartners.add(partner);
        queriedOwners.add(owner);
    }

    private Set<Long> partnersQueried() {
        return new TreeSet<>(queriedPartners.stream().filter(x -> x != null).toList());
    }

    private void login(DataScope scope, Long partnerId, Long userId) {
        String role = scope == DataScope.ALL ? "SUPER_ADMIN" : "ROLE_X";
        TenantContext.set(new CurrentUser(userId, "Tester", 10L, role, scope, partnerId));
    }

    private void viewing(Long viewPartnerId) {
        CurrentUser u = TenantContext.get();
        u.setReadRequest(true);
        u.setViewPartnerId(viewPartnerId);
    }

    private ListAppender<ILoggingEvent> capture() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DashboardServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void release(ListAppender<ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DashboardServiceImpl.class)).detachAppender(appender);
    }

    private void verifyNoPlatformQuery() {
        verify(queries, never()).platformQuotationTotals();
        verify(queries, never()).platformTopPartners(any(), any());
        verify(queries, never()).platformCountPartners(any(), any());
        verify(queries, never()).platformPendingReferralPayouts();
    }

    // ------------------------------------------------------------------ tenant scoping

    @Test
    void partnerB_seesOnlyItsOwnNumbers_andOnlyBIsQueried() {
        login(DataScope.PARTNER, B, 7L);

        DashboardSummaryResponseDto d = service().summary();

        assertEquals(1, d.getTotalQuotations());
        assertEquals(1, d.getPendingQuotations());
        assertEquals(bd("10000"), d.getTotalQuotationValue());
        assertEquals(1, d.getTotalCustomers());
        assertEquals(Set.of(B), partnersQueried());
        assertFalse(queriedPartners.contains(null));              // never an "all partners" query
        verifyNoPlatformQuery();
    }

    @Test
    void partnerB_cannotViewPartnerA_viewPartnerIdIsIgnored() {
        login(DataScope.PARTNER, B, 7L);
        viewing(A);

        DashboardSummaryResponseDto d = service().summary();
        List<DashboardRecentQuotationResponseDto> recent = service().recent(6);

        assertEquals(1, d.getTotalQuotations());
        assertEquals(Set.of(B), partnersQueried());
        assertTrue(recent.isEmpty());
        assertEquals(DashboardScopeResponseDto.OWN, d.getScope().getMode());
        assertEquals("My workspace", d.getScope().getLabel());
    }

    @Test
    void superAdminDefault_isThePlatformTenantsOwnData_notAllPartners() {
        login(DataScope.ALL, PLATFORM, 1L);

        DashboardSummaryResponseDto d = service().summary();

        assertEquals(0, d.getTotalQuotations());
        assertEquals(Set.of(PLATFORM), partnersQueried());
        verifyNoPlatformQuery();
        assertEquals("My workspace", d.getScope().getLabel());
        assertEquals(DashboardScopeResponseDto.OWN, d.getScope().getMode());
        assertFalse(d.getScope().isReadOnly());
        verify(partnerRefs, never()).ref(any());                   // no lookup needed for the own workspace
    }

    @Test
    void superAdminViewingA_getsAsNumbers_withThePartnerNameAsLabel_andReadOnly() {
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);

        DashboardSummaryResponseDto d = service().summary();

        assertEquals(5, d.getTotalQuotations());
        assertEquals(Set.of(A), partnersQueried());
        assertEquals(DashboardScopeResponseDto.PARTNER_VIEW, d.getScope().getMode());
        assertEquals("Alpha Solar", d.getScope().getLabel());
        assertEquals("CP-A", d.getScope().getPartnerCode());
        assertEquals(A, d.getScope().getPartnerId());
        assertTrue(d.getScope().isReadOnly());
        verifyNoPlatformQuery();
    }

    @Test
    void viewPartnerId_isOnlyHonouredOnAGet() {
        login(DataScope.ALL, PLATFORM, 1L);
        CurrentUser u = TenantContext.get();
        u.setReadRequest(false);
        u.setViewPartnerId(A);

        service().summary();

        assertEquals(Set.of(PLATFORM), partnersQueried());
    }

    @Test
    void partnerIdSentToCharts_isIgnored_andLoggedAtWarn() {
        ListAppender<ILoggingEvent> appender = capture();
        try {
            login(DataScope.PARTNER, B, 7L);
            service().charts(A, null, null);

            assertEquals(Set.of(B), partnersQueried());
            ILoggingEvent e = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[DASHBOARD] client partnerId ignored")).findFirst().orElseThrow();
            assertEquals(Level.WARN, e.getLevel());
        } finally {
            release(appender);
        }
    }

    @Test
    void dataScopeOwn_passesTheOwnerFilterToEveryQuery_andHidesPartnerLevelReferralIncome() {
        login(DataScope.OWN, A, 5L);

        DashboardSummaryResponseDto d = service().summary();

        assertFalse(queriedOwners.isEmpty());
        assertTrue(queriedOwners.stream().allMatch(o -> Long.valueOf(5L).equals(o)));
        assertNull(d.getTotalReferralIncome());
        assertTrue(d.getScope().isOwnRecordsOnly());
        verify(queries, never()).referralTotalsAsReferrer(any());
    }

    @Test
    void dataScopePartner_hasNoOwnerFilter() {
        login(DataScope.PARTNER, A, 5L);
        service().summary();
        assertTrue(queriedOwners.stream().allMatch(o -> o == null));
        assertFalse(service().summary().getScope().isOwnRecordsOnly());
    }

    // ------------------------------------------------------------------ the numbers

    @Test
    void summary_mapsEveryFigure() {
        login(DataScope.PARTNER, A, 5L);

        DashboardSummaryResponseDto d = service().summary();

        assertEquals(5, d.getTotalQuotations());
        assertEquals(bd("500000"), d.getTotalQuotationValue());
        assertEquals(2, d.getPendingQuotations());
        assertEquals(2, d.getPendingApprovals());
        assertEquals(1, d.getAcceptedQuotations());
        assertEquals(1, d.getRejectedQuotations());
        assertEquals(20, d.getWinRatePercent());                    // 1 of 5; APPROVED counts in the total only
        assertEquals(1, d.getTodayQuotations());
        assertEquals(3, d.getMonthQuotations());
        assertEquals(bd("300000"), d.getMonthQuotationValue());
        assertEquals(2, d.getSmallSystems());
        assertEquals(2, d.getMediumSystems());
        assertEquals(1, d.getLargeSystems());
        assertEquals(7, d.getTotalCustomers());
        assertEquals(4, d.getTotalProjects());
        assertEquals(bd("1500"), d.getTotalReferralIncome());       // APPROVED + PAID, not PENDING
    }

    @Test
    void summary_usesTheKolkataCalendar() {
        login(DataScope.PARTNER, A, 5L);
        service().summary();
        // 2026-11-14T10:00Z is 15:30 IST: the same day
        assertEquals(List.of(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), periodArgs);
    }

    @Test
    void summary_ofAnEmptyWorkspace_isAllZeros_withoutDividingByZero() {
        login(DataScope.PARTNER, 99L, 5L);
        DashboardSummaryResponseDto d = service().summary();
        assertEquals(0, d.getTotalQuotations());
        assertEquals(0, d.getWinRatePercent());
        assertEquals(bd("0"), d.getTotalQuotationValue());
    }

    @Test
    void recent_mapsRows_missingCustomerBecomesDash_partnerCodeOnlyWhenViewing() {
        login(DataScope.PARTNER, A, 5L);
        List<DashboardRecentQuotationResponseDto> own = service().recent(6);
        assertEquals(2, own.size());
        assertEquals("SQ-1", own.get(0).getCode());
        assertEquals("Ravi", own.get(0).getCustomerName());
        assertEquals("—", own.get(1).getCustomerName());
        assertNull(own.get(0).getPartnerCode());

        TenantContext.clear();
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);
        List<DashboardRecentQuotationResponseDto> viewed = service().recent(6);
        assertEquals("CP-A", viewed.get(0).getPartnerCode());
        verify(partnerRefs).ref(A);                                  // one lookup for the whole list, not one per row
    }

    // ------------------------------------------------------------------ admin endpoint

    @Test
    void adminSummary_refusesNonSuperAdmin_beforeRunningAnyQuery() {
        doThrow(new ForbiddenException("FORBIDDEN", "Only Super Admin can perform this action"))
                .when(guard).requireSuperAdmin(any());
        login(DataScope.PARTNER, A, 5L);

        assertThrows(ForbiddenException.class, () -> service().adminSummary());

        verifyNoInteractions(queries);
    }

    @Test
    void adminSummary_returnsCrossPartnerTotals_forSuperAdmin() {
        when(queries.platformQuotationTotals()).thenReturn(List.<Object[]>of(new Object[]{9L, new BigDecimal("900000")}));
        when(queries.platformCountPartners(any(), any())).thenAnswer(inv -> inv.getArgument(1) == null ? 4L : 3L);
        when(queries.platformPendingReferralPayouts()).thenReturn(new BigDecimal("2500"));
        when(queries.platformTopPartners(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{A, "Alpha Solar", "CP-A", 5L, new BigDecimal("500000")}));
        login(DataScope.ALL, PLATFORM, 1L);

        AdminDashboardResponseDto d = service().adminSummary();

        verify(guard).requireSuperAdmin(any());
        assertEquals(9, d.getPlatformQuotations());
        assertEquals(bd("900000"), d.getPlatformRevenue());
        assertEquals(4, d.getTotalPartners());
        assertEquals(3, d.getActivePartners());
        assertEquals(bd("2500"), d.getPlatformPendingReferralPayouts());
        assertEquals(1, d.getTopPartners().size());
        assertEquals("Alpha Solar", d.getTopPartners().get(0).getPartnerName());
        assertEquals(A, d.getTopPartners().get(0).getPartnerId());
    }

    @Test
    void adminSummary_isNotAffectedByViewPartnerId_andTouchesNoTenantQuery() {
        when(queries.platformQuotationTotals()).thenReturn(List.<Object[]>of(new Object[]{1L, BigDecimal.ONE}));
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);

        service().adminSummary();

        verify(queries, never()).statusTotals(any(), any());
        verify(queries, never()).recent(any(), any(), any());
    }

    // ------------------------------------------------------------------ [DASHBOARD] logging

    @Test
    void everyQueryIsLoggedAtWarn_whenItExceedsTheThreshold() {
        ListAppender<ILoggingEvent> appender = capture();
        try {
            login(DataScope.PARTNER, A, 5L);
            service(-1).summary();                                   // threshold below 0 ms: every query is "slow"

            List<ILoggingEvent> slow = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[DASHBOARD] slow query=")).toList();
            assertTrue(slow.size() >= 6);                            // statusTotals, periodTotals, sizeBuckets, customers, projects, referral
            assertTrue(slow.stream().allMatch(x -> x.getLevel() == Level.WARN));
            assertTrue(slow.stream().anyMatch(x -> x.getFormattedMessage().contains("query=statusTotals partnerId=12")));
            assertTrue(appender.list.stream().anyMatch(x -> x.getFormattedMessage().startsWith("[DASHBOARD] slow endpoint=summary")));
        } finally {
            release(appender);
        }
    }

    @Test
    void fastQueries_areNotLoggedAtWarn() {
        ListAppender<ILoggingEvent> appender = capture();
        try {
            login(DataScope.PARTNER, A, 5L);
            service(60_000).summary();
            assertTrue(appender.list.stream().noneMatch(x -> x.getLevel() == Level.WARN || x.getLevel() == Level.ERROR));
        } finally {
            release(appender);
        }
    }

    @Test
    void aFailingQuery_isLoggedAtErrorWithTheStack_andRethrown() {
        ListAppender<ILoggingEvent> appender = capture();
        try {
            doThrow(new IllegalStateException("db down")).when(queries).countProjects(any(), any());
            login(DataScope.PARTNER, A, 5L);

            assertThrows(IllegalStateException.class, () -> service().summary());

            ILoggingEvent e = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[DASHBOARD] summary failed")).findFirst().orElseThrow();
            assertEquals(Level.ERROR, e.getLevel());
            assertNotNull(e.getThrowableProxy());
        } finally {
            release(appender);
        }
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}