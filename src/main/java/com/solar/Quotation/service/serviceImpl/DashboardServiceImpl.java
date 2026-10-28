package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.Enum.ReferralStatus;
import com.solar.Quotation.dto.responsedto.DashboardRecentQuotationResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardSummaryResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardTopPartnerResponseDto;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.DashboardQueryRepository;
import com.solar.Quotation.repository.ProjectRepository;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.repository.ReferralRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.DashboardService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final int TOP_PARTNERS_LIMIT = 5;
    private static final int RECENT_LIMIT = 10;

    /** System size buckets used by the bar chart: small <= 5 kW, medium <= 20 kW, large above. */
    private static final BigDecimal SMALL_MAX_KW = new BigDecimal("5");
    private static final BigDecimal MEDIUM_MAX_KW = new BigDecimal("20");

    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final ReferralRepository referralRepository;
    private final ChannelPartnerRepository partnerRepository;
    private final DashboardQueryRepository dashboardRepository;
    private final TenantResolver tenantResolver;

    public DashboardServiceImpl(QuotationRepository quotationRepository,
                                CustomerRepository customerRepository,
                                ProjectRepository projectRepository,
                                ReferralRepository referralRepository,
                                ChannelPartnerRepository partnerRepository,
                                DashboardQueryRepository dashboardRepository,
                                TenantResolver tenantResolver) {
        this.quotationRepository = quotationRepository;
        this.customerRepository = customerRepository;
        this.projectRepository = projectRepository;
        this.referralRepository = referralRepository;
        this.partnerRepository = partnerRepository;
        this.dashboardRepository = dashboardRepository;
        this.tenantResolver = tenantResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponseDto summary(Long requestedPartnerId) {
        CurrentUser u = caller();
        boolean sa = u.isSuperAdmin();

        // Partner roles: always their own partner (the requested id is ignored). Super Admin: null = every partner.
        Long partnerId = tenantResolver.effectivePartnerId(requestedPartnerId);
        if (!sa && partnerId == null) {
            throw new ForbiddenException("FORBIDDEN", "No channel partner is linked to this user");
        }
        // Data scope OWN: only records created by this user. Null for everyone else.
        Long ownerId = tenantResolver.ownerFilter();

        DashboardSummaryResponseDto s = new DashboardSummaryResponseDto();

        // ---- core counts ----
        s.setTotalCustomers(customerRepository.countFiltered(partnerId, ownerId, null, null));
        s.setTotalProjects(projectRepository.countFiltered(partnerId, ownerId, null, null));

        long pending = quotationRepository.countFiltered(partnerId, ownerId, QuotationStatus.PENDING);
        long accepted = quotationRepository.countFiltered(partnerId, ownerId, QuotationStatus.ACCEPTED);
        long rejected = quotationRepository.countFiltered(partnerId, ownerId, QuotationStatus.REJECTED);
        long total = pending + accepted + rejected;

        s.setPendingQuotations(pending);
        s.setAcceptedQuotations(accepted);
        s.setRejectedQuotations(rejected);
        s.setTotalQuotations(total);
        s.setPendingApprovals(pending);
        s.setWinRatePercent(total == 0 ? 0 : (int) Math.round(accepted * 100.0 / total));
        s.setTotalQuotationValue(nz(quotationRepository.sumTotal(partnerId, ownerId, null)));

        // ---- today / this month ----
        LocalDate today = LocalDate.now(IST);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        s.setTodayQuotations(dashboardRepository.countBetween(partnerId, ownerId, today, today));
        s.setMonthQuotations(dashboardRepository.countBetween(partnerId, ownerId, monthStart, monthEnd));
        s.setMonthQuotationValue(nz(dashboardRepository.sumBetween(partnerId, ownerId, monthStart, monthEnd)));

        // ---- system size chart ----
        List<Object[]> buckets = dashboardRepository.sizeBuckets(partnerId, ownerId, SMALL_MAX_KW, MEDIUM_MAX_KW);
        if (!buckets.isEmpty()) {
            Object[] b = buckets.get(0);
            s.setSmallSystems(toLong(b[0]));
            s.setMediumSystems(toLong(b[1]));
            s.setLargeSystems(toLong(b[2]));
        }

        // ---- recent quotations ----
        s.setRecentQuotations(recentQuotations(partnerId, ownerId, sa));

        // ---- referrals ----
        if (sa) {
            // approved but not yet paid, platform wide (or for the one partner Super Admin is looking at)
            s.setPlatformPendingReferralPayouts(referralAmount(partnerId, null, ReferralStatus.APPROVED));
        } else {
            // commission this partner earned as the REFERRER: approved + paid (same as "totalEarned" in the Referral module)
            s.setTotalReferralIncome(referralAmount(null, partnerId, ReferralStatus.APPROVED)
                    .add(referralAmount(null, partnerId, ReferralStatus.PAID)));
        }

        // ---- Super Admin only: partner numbers + leaderboard ----
        if (sa) {
            s.setTotalPartners(dashboardRepository.countPartners(PartnerType.PARTNER, null));
            s.setActivePartners(dashboardRepository.countPartners(PartnerType.PARTNER, RecordStatus.ACTIVE));
            s.setTopPartners(topPartners());
        }
        return s;
    }

    // ---------------------------------------------------------------- helpers

    private List<DashboardRecentQuotationResponseDto> recentQuotations(Long partnerId, Long ownerId, boolean sa) {
        List<Object[]> rows = dashboardRepository.recent(partnerId, ownerId, PageRequest.of(0, RECENT_LIMIT));

        Map<Long, String> partnerCodes = new HashMap<>();
        if (sa && !rows.isEmpty()) {
            Set<Long> ids = new HashSet<>();
            for (Object[] r : rows) ids.add((Long) r[2]);
            for (ChannelPartner p : partnerRepository.findAllById(ids)) partnerCodes.put(p.getId(), p.getCode());
        }

        List<DashboardRecentQuotationResponseDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            DashboardRecentQuotationResponseDto d = new DashboardRecentQuotationResponseDto();
            d.setCode((String) r[0]);
            d.setCustomerName((String) r[1]);
            d.setPartnerCode(sa ? partnerCodes.get((Long) r[2]) : null);
            d.setSystemSizeKw((BigDecimal) r[3]);
            d.setTotalAmount((BigDecimal) r[4]);
            d.setStatus(((QuotationStatus) r[5]).name());
            d.setQuoteDate((LocalDate) r[6]);
            out.add(d);
        }
        return out;
    }

    private List<DashboardTopPartnerResponseDto> topPartners() {
        List<Object[]> rows = dashboardRepository.topPartners(PageRequest.of(0, TOP_PARTNERS_LIMIT));
        if (rows.isEmpty()) return List.of();

        Set<Long> ids = new HashSet<>();
        for (Object[] r : rows) ids.add((Long) r[0]);
        Map<Long, ChannelPartner> byId = new HashMap<>();
        for (ChannelPartner p : partnerRepository.findAllById(ids)) byId.put(p.getId(), p);

        List<DashboardTopPartnerResponseDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            ChannelPartner p = byId.get((Long) r[0]);
            DashboardTopPartnerResponseDto d = new DashboardTopPartnerResponseDto();
            d.setPartnerId((Long) r[0]);
            d.setPartnerName(p != null ? p.getName() : "Unknown partner");
            d.setPartnerCode(p != null ? p.getCode() : "—");
            d.setTotalQuotations(toLong(r[1]));
            d.setTotalRevenue(toBig(r[2]));
            out.add(d);
        }
        return out;
    }

    /** Sum of referral amounts in one status. Rows of statusTotals are [status, count, sum(referralAmount)]. */
    private BigDecimal referralAmount(Long ownerPartnerId, Long referrerPartnerId, ReferralStatus status) {
        for (Object[] row : referralRepository.statusTotals(ownerPartnerId, referrerPartnerId)) {
            if (row[0] == status) return toBig(row[2]);
        }
        return BigDecimal.ZERO;
    }

    private CurrentUser caller() {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        return u;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal toBig(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }
}
