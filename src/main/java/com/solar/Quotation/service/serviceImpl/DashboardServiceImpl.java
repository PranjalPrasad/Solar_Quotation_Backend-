package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.responsedto.AdminDashboardResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardRecentQuotationResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardScopeResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardSummaryResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardTopPartnerResponseDto;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.exception.ApiException;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.DashboardQueryRepository;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.SuperAdminGuard;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.DashboardService;
import com.solar.Quotation.service.PartnerRefProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tenant rules (same as Customers / Projects / Calculator; Super Admin works in the PLATFORM tenant like any partner):
 *   summary / recent / charts  READS of ONE workspace -> tenantResolver.effectiveReadPartnerId(): the caller's own
 *                              tenant, or the partner named by ?viewPartnerId= for data scope ALL on a GET.
 *                              Data scope OWN (tenantResolver.ownerFilter()) is applied on every query.
 *                              So a Super Admin's default dashboard is the PLATFORM tenant's own data.
 *   adminSummary               the ONLY cross-partner numbers. Super Admin only (SuperAdminGuard, on top of the
 *                              /api/admin/** path guard), never filtered by workspace, never mixed into summary().
 *
 * Every tenant query takes a non-null partner id (see DashboardQueryRepository), so no code path here can run an
 * "all partners" query by accident.
 *
 * Speed: the summary is 7 index-covered aggregate queries (it was about a dozen, each with "partnerId is null or").
 * The recent list is one LIMITed query with the customer name joined in (no per-row lookups).
 *
 * Logging ([DASHBOARD]): the duration of every query and of every endpoint is logged at DEBUG, and at WARN when it
 * takes longer than app.dashboard.slow-query-ms (default 1000). Unexpected failures are logged at ERROR with the
 * stack. Only ids and counts are logged, never customer names or amounts.
 */
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    static final BigDecimal SMALL_MAX = new BigDecimal("5");
    static final BigDecimal MEDIUM_MAX = new BigDecimal("20");
    static final int DEFAULT_RECENT = 6;
    static final int MAX_RECENT = 20;
    static final int TOP_PARTNERS = 5;

    private final DashboardQueryRepository queries;
    private final TenantResolver tenantResolver;
    private final PartnerRefProvider partnerRefProvider;
    private final SuperAdminGuard superAdminGuard;
    private final long slowMs;
    private final Clock clock;

    @Autowired
    public DashboardServiceImpl(DashboardQueryRepository queries, TenantResolver tenantResolver,
                                PartnerRefProvider partnerRefProvider, SuperAdminGuard superAdminGuard,
                                @Value("${app.dashboard.slow-query-ms:1000}") long slowMs) {
        this(queries, tenantResolver, partnerRefProvider, superAdminGuard, slowMs, Clock.system(KOLKATA));
    }

    /** For tests: a fixed clock. */
    DashboardServiceImpl(DashboardQueryRepository queries, TenantResolver tenantResolver,
                         PartnerRefProvider partnerRefProvider, SuperAdminGuard superAdminGuard,
                         long slowMs, Clock clock) {
        this.queries = queries;
        this.tenantResolver = tenantResolver;
        this.partnerRefProvider = partnerRefProvider;
        this.superAdminGuard = superAdminGuard;
        this.slowMs = slowMs;
        this.clock = clock;
    }

    /** The workspace a read applies to. partnerId is NEVER null. */
    private record Workspace(Long partnerId, Long ownerId, boolean viewing) { }

    private Workspace workspace() {
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long own = tenantResolver.ownPartnerId();
        return new Workspace(partnerId, tenantResolver.ownerFilter(), !partnerId.equals(own));
    }

    // ---------------------------------------------------------------- summary

    @Override
    public DashboardSummaryResponseDto summary() {
        return guarded("summary", () -> {
            long t0 = System.nanoTime();
            Workspace ws = workspace();
            Long p = ws.partnerId();
            Long o = ws.ownerId();
            LocalDate today = LocalDate.now(clock);
            LocalDate monthStart = today.withDayOfMonth(1);
            LocalDate nextMonth = monthStart.plusMonths(1);

            DashboardSummaryResponseDto d = new DashboardSummaryResponseDto();
            d.setScope(scopeOf(ws));

            // status totals: one query gives total, pending, accepted, rejected and the total value
            long total = 0;
            BigDecimal value = BigDecimal.ZERO;
            for (Object[] row : timed("statusTotals", p, () -> queries.statusTotals(p, o))) {
                String status = String.valueOf(row[0]);
                long count = num(row[1]);
                total += count;
                value = value.add(big(row[2]));
                switch (status) {
                    case "PENDING" -> d.setPendingQuotations(count);
                    case "ACCEPTED" -> d.setAcceptedQuotations(count);
                    case "REJECTED" -> d.setRejectedQuotations(count);
                    default -> { /* APPROVED / PAID / EXPIRED count in the total only */ }
                }
            }
            d.setTotalQuotations(total);
            d.setTotalQuotationValue(value);
            d.setPendingApprovals(d.getPendingQuotations());
            d.setWinRatePercent(total == 0 ? 0 : (int) Math.round(d.getAcceptedQuotations() * 100.0 / total));

            Object[] period = first(timed("periodTotals", p, () -> queries.periodTotals(p, o, today, monthStart, nextMonth)));
            d.setTodayQuotations(num(at(period, 0)));
            d.setMonthQuotations(num(at(period, 1)));
            d.setMonthQuotationValue(big(at(period, 2)));

            Object[] sizes = first(timed("sizeBuckets", p, () -> queries.sizeBuckets(p, o, SMALL_MAX, MEDIUM_MAX)));
            d.setSmallSystems(num(at(sizes, 0)));
            d.setMediumSystems(num(at(sizes, 1)));
            d.setLargeSystems(num(at(sizes, 2)));

            d.setTotalCustomers(timed("countCustomers", p, () -> queries.countCustomers(p, o)));
            d.setTotalProjects(timed("countProjects", p, () -> queries.countProjects(p, o)));

            if (o == null) {                                         // not for data scope OWN: it is a partner level number
                BigDecimal income = BigDecimal.ZERO;
                for (Object[] row : timed("referralTotals", p, () -> queries.referralTotalsAsReferrer(p))) {
                    String st = String.valueOf(row[0]);
                    if ("APPROVED".equals(st) || "PAID".equals(st)) income = income.add(big(row[1]));
                }
                d.setTotalReferralIncome(income);
            }
            logTotal("summary", p, t0);
            return d;
        });
    }

    // ---------------------------------------------------------------- recent

    @Override
    public List<DashboardRecentQuotationResponseDto> recent(int limit) {
        return guarded("recent", () -> {
            long t0 = System.nanoTime();
            Workspace ws = workspace();
            Long p = ws.partnerId();
            Long o = ws.ownerId();
            int n = Math.min(Math.max(limit, 1), MAX_RECENT);

            List<Object[]> rows = timed("recent", p, () -> queries.recent(p, o, PageRequest.of(0, n)));
            String partnerCode = null;
            if (ws.viewing()) {                                      // one lookup for the whole list, not one per row
                PartnerRefResponseDto ref = partnerRefProvider.ref(p);
                partnerCode = ref == null ? null : ref.getCode();
            }
            List<DashboardRecentQuotationResponseDto> out = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                DashboardRecentQuotationResponseDto d = new DashboardRecentQuotationResponseDto();
                d.setCode((String) r[0]);
                d.setCustomerName(r[1] == null ? "—" : (String) r[1]);
                d.setPartnerCode(partnerCode);
                d.setSystemSizeKw((BigDecimal) r[2]);
                d.setTotalAmount((BigDecimal) r[3]);
                d.setStatus(String.valueOf(r[4]));
                d.setQuoteDate((LocalDate) r[5]);
                out.add(d);
            }
            logTotal("recent", p, t0);
            return out;
        });
    }

    // ---------------------------------------------------------------- charts (legacy endpoint, same workspace)

    @Override
    public DashboardResponseDto charts(Long partnerIdIgnored, LocalDate from, LocalDate to) {
        return guarded("charts", () -> {
            if (partnerIdIgnored != null) {
                log.warn("[DASHBOARD] client partnerId ignored on read requestedPartnerId={}", partnerIdIgnored);
            }
            long t0 = System.nanoTime();
            Workspace ws = workspace();
            Long p = ws.partnerId();
            Long o = ws.ownerId();

            DashboardResponseDto d = new DashboardResponseDto();
            Map<String, Long> statusCounts = new LinkedHashMap<>();
            for (Object[] r : timed("statusTotals", p, () -> queries.statusTotals(p, o))) {
                statusCounts.put(String.valueOf(r[0]), num(r[1]));
            }
            d.setStatusCounts(statusCounts);

            Object[] sizes = first(timed("sizeBuckets", p, () -> queries.sizeBuckets(p, o, SMALL_MAX, MEDIUM_MAX)));
            List<DashboardResponseDto.SizeBucket> buckets = new ArrayList<>();
            buckets.add(new DashboardResponseDto.SizeBucket("Small (≤5 kW)", num(at(sizes, 0))));
            buckets.add(new DashboardResponseDto.SizeBucket("Medium (6–20 kW)", num(at(sizes, 1))));
            buckets.add(new DashboardResponseDto.SizeBucket("Large (>20 kW)", num(at(sizes, 2))));
            d.setSizeDistribution(buckets);

            List<DashboardResponseDto.MonthlyPoint> trend = new ArrayList<>();
            for (Object[] r : timed("monthlyTrend", p, () -> queries.monthlyTrend(p, o, from, to))) {
                trend.add(new DashboardResponseDto.MonthlyPoint((String) r[0], num(r[1]), big(r[2])));
            }
            d.setMonthlyTrend(trend);
            logTotal("charts", p, t0);
            return d;
        });
    }

    // ---------------------------------------------------------------- admin (cross-partner)

    @Override
    public AdminDashboardResponseDto adminSummary() {
        superAdminGuard.requireSuperAdmin("admin dashboard");        // first: nothing below runs for anybody else
        return guarded("admin summary", () -> {
            long t0 = System.nanoTime();
            log.info("[DASHBOARD] cross-partner totals requested {}", Actors.describe());

            AdminDashboardResponseDto d = new AdminDashboardResponseDto();
            Object[] totals = first(timed("platformQuotationTotals", null, queries::platformQuotationTotals));
            d.setPlatformQuotations(num(at(totals, 0)));
            d.setPlatformRevenue(big(at(totals, 1)));
            d.setTotalPartners(timed("platformCountPartners", null,
                    () -> queries.platformCountPartners(PartnerType.PARTNER, null)));
            d.setActivePartners(timed("platformCountActivePartners", null,
                    () -> queries.platformCountPartners(PartnerType.PARTNER, RecordStatus.ACTIVE)));
            d.setPlatformPendingReferralPayouts(big(timed("platformPendingReferralPayouts", null,
                    queries::platformPendingReferralPayouts)));

            List<DashboardTopPartnerResponseDto> top = new ArrayList<>();
            for (Object[] r : timed("platformTopPartners", null,
                    () -> queries.platformTopPartners(PartnerType.PARTNER, PageRequest.of(0, TOP_PARTNERS)))) {
                DashboardTopPartnerResponseDto t = new DashboardTopPartnerResponseDto();
                t.setPartnerId(((Number) r[0]).longValue());
                t.setPartnerName((String) r[1]);
                t.setPartnerCode((String) r[2]);
                t.setTotalQuotations(num(r[3]));
                t.setTotalRevenue(big(r[4]));
                top.add(t);
            }
            d.setTopPartners(top);
            logTotal("admin summary", null, t0);
            return d;
        });
    }

    // ---------------------------------------------------------------- scope label

    private DashboardScopeResponseDto scopeOf(Workspace ws) {
        DashboardScopeResponseDto s = new DashboardScopeResponseDto();
        s.setPartnerId(ws.partnerId());
        s.setOwnRecordsOnly(ws.ownerId() != null);
        if (!ws.viewing()) {
            s.setMode(DashboardScopeResponseDto.OWN);
            s.setLabel("My workspace");
            s.setReadOnly(false);
            return s;
        }
        PartnerRefResponseDto ref = partnerRefProvider.ref(ws.partnerId());
        s.setMode(DashboardScopeResponseDto.PARTNER_VIEW);
        s.setPartnerName(ref == null ? null : ref.getName());
        s.setPartnerCode(ref == null ? null : ref.getCode());
        s.setLabel(ref != null && ref.getName() != null ? ref.getName() : "Partner #" + ws.partnerId());
        s.setReadOnly(true);
        return s;
    }

    // ---------------------------------------------------------------- timing + errors

    /** Runs one query and logs its duration: DEBUG normally, WARN when it exceeds the slow-query threshold. */
    private <T> T timed(String name, Long partnerId, Supplier<T> query) {
        long t0 = System.nanoTime();
        try {
            return query.get();
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (ms > slowMs) {
                log.warn("[DASHBOARD] slow query={} partnerId={} took={}ms threshold={}ms", name, partnerId, ms, slowMs);
            } else {
                log.debug("[DASHBOARD] query={} partnerId={} took={}ms", name, partnerId, ms);
            }
        }
    }

    private void logTotal(String endpoint, Long partnerId, long t0) {
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (ms > slowMs) {
            log.warn("[DASHBOARD] slow endpoint={} partnerId={} took={}ms threshold={}ms", endpoint, partnerId, ms, slowMs);
        } else {
            log.debug("[DASHBOARD] endpoint={} partnerId={} took={}ms", endpoint, partnerId, ms);
        }
    }

    /** Anything unexpected is logged at ERROR with the stack, then rethrown to the API error handler. */
    private <T> T guarded(String operation, Supplier<T> body) {
        try {
            return body.get();
        } catch (BusinessException | ResourceNotFoundException | ForbiddenException | ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[DASHBOARD] {} failed unexpectedly {}", operation, Actors.describe(), e);
            throw e;
        }
    }

    // ---------------------------------------------------------------- row helpers

    private static Object[] first(List<Object[]> rows) {
        return rows == null || rows.isEmpty() ? new Object[0] : rows.get(0);
    }

    private static Object at(Object[] row, int i) {
        return row.length > i ? row[i] : null;
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal big(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}