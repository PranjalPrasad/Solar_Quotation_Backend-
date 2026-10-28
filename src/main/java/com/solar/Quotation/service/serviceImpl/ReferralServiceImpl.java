package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.Enum.ReferralPaymentMode;
import com.solar.Quotation.Enum.ReferralStatus;
import com.solar.Quotation.dto.requestdto.ReferralFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ReferralPaidRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerRefResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerOptionResponseDto;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralPartnerTotalsResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralSummaryResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.entity.Referral;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.repository.ReferralRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.ReferralService;
import com.solar.Quotation.service.UserNameProvider;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.DiffUtil;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Visibility rules (the channel partner isolation of this module):
 *  - Super Admin: every referral, can filter by owner partner (partnerId) and by earning partner (referrerPartnerId).
 *  - Partner user: ONLY the referrals their own partner EARNED. They never see the customer, the owner partner,
 *    remarks or audit of the quotation (those belong to another partner). Another partner's id gives 404.
 * Lifecycle (follows the quotation, nothing to approve by hand):
 *  quotation created with a partner referrer -> PENDING; quotation ACCEPTED -> APPROVED; quotation REJECTED -> REJECTED;
 *  Super Admin marks APPROVED as PAID.
 */
@Service
public class ReferralServiceImpl implements ReferralService {

    private static final Set<String> SORT_FIELDS =
            Set.of("quoteDate", "referralAmount", "quotationAmount", "status", "referrerName", "code", "createdAt");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final int MAX_PARTNER_IDS = 200;

    private final ReferralRepository referralRepository;
    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final PartnerRefProvider partnerRefProvider;
    private final UserNameProvider userNameProvider;
    private final AuditInfoMapper auditInfoMapper;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;
    private final ChannelPartnerRepository channelPartnerRepository;

    public ReferralServiceImpl(ReferralRepository referralRepository, QuotationRepository quotationRepository,
                               CustomerRepository customerRepository, PartnerRefProvider partnerRefProvider,
                               UserNameProvider userNameProvider, AuditInfoMapper auditInfoMapper,
                               AuditService auditService, CodeGenerator codeGenerator,
                               ChannelPartnerRepository channelPartnerRepository) {
        this.referralRepository = referralRepository;
        this.quotationRepository = quotationRepository;
        this.customerRepository = customerRepository;
        this.partnerRefProvider = partnerRefProvider;
        this.userNameProvider = userNameProvider;
        this.auditInfoMapper = auditInfoMapper;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.channelPartnerRepository = channelPartnerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerOptionResponseDto> referrerOptions() {
        CurrentUser u = caller();
        Long own = u.isSuperAdmin() ? null : u.getPartnerId();
        return channelPartnerRepository.findOptions(PartnerType.PARTNER, RecordStatus.ACTIVE).stream()
                .filter(p -> own == null || !p.getId().equals(own))
                .map(p -> new PartnerOptionResponseDto(p.getId(), p.getCode(), p.getName()))
                .toList();
    }

    // ---------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReferralResponseDto> list(ReferralFilterRequestDto f) {
        CurrentUser u = caller();
        boolean sa = u.isSuperAdmin();
        Long ownerPartnerId = sa ? f.getPartnerId() : null;
        Long referrerPartnerId = sa ? f.getReferrerPartnerId() : requiredPartner(u);
        LocalDate from = f.getFromDate() == null ? MIN_DATE : f.getFromDate();
        LocalDate to = f.getToDate() == null ? MAX_DATE : f.getToDate();
        Page<Referral> page = referralRepository.search(ownerPartnerId, referrerPartnerId, parseStatus(f.getStatus()),
                from, to, f.getSearchOrNull(), sa, f.toPageable("quoteDate,desc", SORT_FIELDS));
        Lookup lk = lookup(page.getContent(), sa);
        return PageResponse.of(page, r -> toResponse(r, lk, sa));
    }

    @Override
    @Transactional(readOnly = true)
    public ReferralSummaryResponseDto summary(Long partnerId, Long referrerPartnerId) {
        CurrentUser u = caller();
        boolean sa = u.isSuperAdmin();
        Long ownerPartnerId = sa ? partnerId : null;
        Long referrer = sa ? referrerPartnerId : requiredPartner(u);

        ReferralSummaryResponseDto s = new ReferralSummaryResponseDto();
        for (Object[] row : referralRepository.statusTotals(ownerPartnerId, referrer)) {
            ReferralStatus st = (ReferralStatus) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal amount = toBig(row[2]);
            switch (st) {
                case PENDING -> { s.setPendingCount(count); s.setPendingAmount(amount); }
                case APPROVED -> { s.setApprovedCount(count); s.setPendingPayout(amount); }
                case PAID -> { s.setPaidCount(count); s.setPaidAmount(amount); }
                case REJECTED -> s.setRejectedCount(count);
            }
        }
        // a rejected referral never happened, so it is not counted as "made"
        s.setTotalReferrals(s.getPendingCount() + s.getApprovedCount() + s.getPaidCount());
        s.setTotalEarned(s.getPendingPayout().add(s.getPaidAmount()));

        LocalDate today = LocalDate.now(IST);
        s.setPaidThisMonth(toBig(referralRepository.sumPaidBetween(ownerPartnerId, referrer, ReferralStatus.PAID,
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))));
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public ReferralResponseDto get(Long id) {
        CurrentUser u = caller();
        return detail(findVisible(id, u), u.isSuperAdmin());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> history(Long id) {
        requireSuperAdmin(caller(), "Only Super Admin can see the referral history");
        Referral r = referralRepository.findOne(id, null, null)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found"));
        return auditService.entityHistory("Referral", r.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferralPartnerTotalsResponseDto> partnerTotals(Collection<Long> partnerIds) {
        requireSuperAdmin(caller(), "Only Super Admin can see partner referral totals");
        if (partnerIds == null || partnerIds.isEmpty()) {
            return List.of();
        }
        if (partnerIds.size() > MAX_PARTNER_IDS) {
            throw new BusinessException("VALIDATION_ERROR", "Send at most " + MAX_PARTNER_IDS + " partner ids at a time");
        }
        Map<Long, ReferralPartnerTotalsResponseDto> byPartner = new LinkedHashMap<>();
        for (Long id : partnerIds) {
            ReferralPartnerTotalsResponseDto t = new ReferralPartnerTotalsResponseDto();
            t.setPartnerId(id);
            byPartner.put(id, t);
        }
        for (Object[] row : referralRepository.totalsByReferrer(partnerIds)) {
            ReferralPartnerTotalsResponseDto t = byPartner.get(((Number) row[0]).longValue());
            if (t == null) {
                continue;
            }
            ReferralStatus st = (ReferralStatus) row[1];
            long count = ((Number) row[2]).longValue();
            BigDecimal amount = toBig(row[3]);
            if (st == ReferralStatus.PENDING) {
                t.setTotalReferrals(t.getTotalReferrals() + count);
            } else if (st == ReferralStatus.APPROVED) {
                t.setTotalReferrals(t.getTotalReferrals() + count);
                t.setPendingPayout(amount);
                t.setTotalEarned(t.getTotalEarned().add(amount));
            } else if (st == ReferralStatus.PAID) {
                t.setTotalReferrals(t.getTotalReferrals() + count);
                t.setPaidAmount(amount);
                t.setTotalEarned(t.getTotalEarned().add(amount));
            }
        }
        return List.copyOf(byPartner.values());
    }

    // ---------------------------------------------------------------- write

    @Override
    @Transactional
    public ReferralResponseDto markPaid(Long id, ReferralPaidRequestDto req) {
        CurrentUser u = caller();
        requireSuperAdmin(u, "Only Super Admin can mark a referral as paid");
        Referral r = referralRepository.findOne(id, null, null)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found"));
        if (r.getStatus() != ReferralStatus.APPROVED) {
            throw new BusinessException("BUSINESS_RULE",
                    "Only an approved referral can be marked as paid (this one is " + r.getStatus().name() + ")");
        }
        LocalDate today = LocalDate.now(IST);
        if (req.getPaidOn().isAfter(today)) {
            throw new BusinessException("VALIDATION_ERROR", "Paid date cannot be in the future");
        }
        LocalDate earliest = r.getAcceptedOn() != null ? r.getAcceptedOn() : r.getQuoteDate();
        if (req.getPaidOn().isBefore(earliest)) {
            throw new BusinessException("VALIDATION_ERROR", "Paid date cannot be before the quotation was accepted");
        }
        ReferralPaymentMode mode = ReferralPaymentMode.valueOf(req.getPaymentMode());
        String reference = blankToNull(req.getPaymentReference());
        if (mode != ReferralPaymentMode.CASH && reference == null) {
            throw new BusinessException("VALIDATION_ERROR", "Payment reference is required for " + mode.name());
        }

        Map<String, Object> before = snapshot(r);
        r.setStatus(ReferralStatus.PAID);
        r.setPaidOn(req.getPaidOn());
        r.setPaidBy(u.getUserId());
        r.setPaymentMode(mode);
        r.setPaymentReference(reference);
        r.setRemarks(blankToNull(req.getRemarks()));
        Referral saved = referralRepository.save(r);

        quotationRepository.findById(saved.getQuotationId()).ifPresent(q -> {
            q.setReferralStatus(ReferralStatus.PAID);
            quotationRepository.save(q);
        });

        auditService.record(AuditAction.MARK_PAID, "REFERRAL", "Referral", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), DiffUtil.diff(before, snapshot(saved)));
        return detail(saved, true);
    }

    // ---------------------------------------------------------------- hooks from the Quotation module

    @Override
    @Transactional
    public void syncFromQuotation(Quotation q) {
        Referral existing = referralRepository.findByQuotationIdAndDeletedFalse(q.getId()).orElse(null);
        boolean payable = q.isReferred() && q.getReferrerPartnerId() != null;
        if (!payable) {
            if (existing != null) {
                removeRow(existing);
            }
            q.setReferralStatus(null);
            return;
        }
        if (existing != null && existing.getStatus() == ReferralStatus.PAID) {
            throw new BusinessException("BUSINESS_RULE", "The referral of this quotation is already paid and cannot be changed");
        }

        Referral r = existing != null ? existing : new Referral();
        Map<String, Object> before = existing == null ? null : snapshot(existing);
        if (existing == null) {
            r.setChannelPartnerId(q.getChannelPartnerId());
            r.setCode(codeGenerator.next("RF", q.getChannelPartnerId()));
            r.setQuotationId(q.getId());
        }
        r.setQuotationCode(q.getCode());
        r.setCustomerId(q.getCustomerId());
        r.setReferrerPartnerId(q.getReferrerPartnerId());
        r.setReferrerName(q.getReferredBy());
        r.setReferrerCode(q.getReferralCode());
        r.setQuotationAmount(q.getTotalAmount());
        r.setReferralPercent(q.getReferralPercent() == null ? BigDecimal.ZERO : q.getReferralPercent());
        r.setReferralAmount(q.getReferralAmount());
        r.setQuoteDate(q.getQuoteDate());
        ReferralStatus target = statusFor(q.getStatus());
        r.setStatus(target);
        r.setAcceptedOn(target == ReferralStatus.APPROVED
                ? (r.getAcceptedOn() != null ? r.getAcceptedOn() : LocalDate.now(IST)) : null);
        Referral saved = referralRepository.save(r);
        q.setReferralStatus(saved.getStatus());

        auditService.record(existing == null ? AuditAction.CREATE : AuditAction.UPDATE, "REFERRAL", "Referral",
                saved.getId(), saved.getCode(), saved.getChannelPartnerId(), DiffUtil.diff(before, snapshot(saved)));
    }

    @Override
    @Transactional
    public void onQuotationStatusChanged(Quotation q) {
        Referral r = referralRepository.findByQuotationIdAndDeletedFalse(q.getId()).orElse(null);
        if (r == null) {
            return;
        }
        if (r.getStatus() == ReferralStatus.PAID) {
            if (q.getStatus() == QuotationStatus.ACCEPTED) {
                q.setReferralStatus(ReferralStatus.PAID);
                return;
            }
            throw new BusinessException("BUSINESS_RULE",
                    "The referral of this quotation is already paid, so its status cannot be changed");
        }
        ReferralStatus target = statusFor(q.getStatus());
        q.setReferralStatus(target);
        if (r.getStatus() == target) {
            return;
        }
        Map<String, Object> before = snapshot(r);
        r.setStatus(target);
        r.setAcceptedOn(target == ReferralStatus.APPROVED ? LocalDate.now(IST) : null);
        Referral saved = referralRepository.save(r);
        auditService.record(AuditAction.STATUS_CHANGE, "REFERRAL", "Referral", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), DiffUtil.diff(before, snapshot(saved)));
    }

    @Override
    @Transactional
    public void onQuotationDeleted(Quotation q) {
        referralRepository.findByQuotationIdAndDeletedFalse(q.getId()).ifPresent(this::removeRow);
    }

    // ---------------------------------------------------------------- helpers

    private CurrentUser caller() {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        return u;
    }

    /** A partner user without a partner id would match every referrer, so that case is refused. */
    private Long requiredPartner(CurrentUser u) {
        if (u.getPartnerId() == null) {
            throw new ForbiddenException("FORBIDDEN", "No channel partner is linked to this user");
        }
        return u.getPartnerId();
    }

    private void requireSuperAdmin(CurrentUser u, String message) {
        if (!u.isSuperAdmin()) {
            throw new ForbiddenException("FORBIDDEN", message);
        }
    }

    private Referral findVisible(Long id, CurrentUser u) {
        boolean sa = u.isSuperAdmin();
        return referralRepository.findOne(id, null, sa ? null : requiredPartner(u))
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found"));
    }

    private void removeRow(Referral r) {
        if (r.getStatus() == ReferralStatus.PAID) {
            throw new BusinessException("BUSINESS_RULE", "The referral of this quotation is already paid and cannot be removed");
        }
        r.setDeleted(true);
        referralRepository.save(r);
        auditService.record(AuditAction.DELETE, "REFERRAL", "Referral", r.getId(), r.getCode(),
                r.getChannelPartnerId(), null);
    }

    private ReferralStatus statusFor(QuotationStatus s) {
        return switch (s) {
            case ACCEPTED -> ReferralStatus.APPROVED;
            case REJECTED -> ReferralStatus.REJECTED;
            case PENDING -> ReferralStatus.PENDING;
        };
    }

    private ReferralStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ReferralStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("VALIDATION_ERROR", "Unknown referral status " + value);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private BigDecimal toBig(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(o));
    }

    private Map<String, Object> snapshot(Referral r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus().name());
        m.put("referrerName", r.getReferrerName());
        m.put("referrerCode", r.getReferrerCode());
        m.put("quotationAmount", r.getQuotationAmount() == null ? null : r.getQuotationAmount().stripTrailingZeros().toPlainString());
        m.put("referralPercent", r.getReferralPercent() == null ? null : r.getReferralPercent().stripTrailingZeros().toPlainString());
        m.put("referralAmount", r.getReferralAmount() == null ? null : r.getReferralAmount().stripTrailingZeros().toPlainString());
        m.put("acceptedOn", r.getAcceptedOn() == null ? null : r.getAcceptedOn().toString());
        m.put("paidOn", r.getPaidOn() == null ? null : r.getPaidOn().toString());
        m.put("paymentMode", r.getPaymentMode() == null ? null : r.getPaymentMode().name());
        m.put("paymentReference", r.getPaymentReference());
        m.put("remarks", r.getRemarks());
        return m;
    }

    // ---------------------------------------------------------------- mapping

    private static final class Lookup {
        private Map<Long, PartnerRefResponseDto> partners = new HashMap<>();
        private Map<Long, String> names = new HashMap<>();
        private final Map<Long, CustomerRefResponseDto> customers = new HashMap<>();
    }

    /** Customers, owner partners and user names are only loaded for Super Admin (the others never see them). */
    private Lookup lookup(List<Referral> rows, boolean sa) {
        Lookup lk = new Lookup();
        if (rows.isEmpty() || !sa) {
            return lk;
        }
        Set<Long> customerIds = new HashSet<>();
        Set<Long> partnerIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Referral r : rows) {
            customerIds.add(r.getCustomerId());
            partnerIds.add(r.getChannelPartnerId());
            if (r.getCreatedBy() != null) userIds.add(r.getCreatedBy());
            if (r.getUpdatedBy() != null) userIds.add(r.getUpdatedBy());
            if (r.getPaidBy() != null) userIds.add(r.getPaidBy());
        }
        for (Customer c : customerRepository.findAllById(customerIds)) {
            lk.customers.put(c.getId(), new CustomerRefResponseDto(c.getId(), c.getCode(), c.getName()));
        }
        lk.partners = partnerRefProvider.refs(partnerIds);
        lk.names = userNameProvider.names(userIds);
        return lk;
    }

    private ReferralResponseDto detail(Referral r, boolean sa) {
        return toResponse(r, lookup(List.of(r), sa), sa);
    }

    private ReferralResponseDto toResponse(Referral r, Lookup lk, boolean sa) {
        ReferralResponseDto d = new ReferralResponseDto();
        d.setId(r.getId());
        d.setCode(r.getCode());
        d.setStatus(r.getStatus().name());
        d.setQuotation(new ReferralResponseDto.QuotationRef(r.getQuotationId(), r.getQuotationCode()));
        d.setReferrer(new ReferralResponseDto.Referrer("CHANNEL_PARTNER", r.getReferrerPartnerId(),
                r.getReferrerName(), r.getReferrerCode()));
        d.setQuotationAmount(r.getQuotationAmount());
        d.setReferralPercent(r.getReferralPercent());
        d.setReferralAmount(r.getReferralAmount());
        d.setQuoteDate(r.getQuoteDate());
        d.setAcceptedOn(r.getAcceptedOn());
        d.setPaidOn(r.getPaidOn());
        d.setPaymentMode(r.getPaymentMode() == null ? null : r.getPaymentMode().name());
        d.setPaymentReference(r.getPaymentReference());
        if (sa) {
            d.setReferredCustomer(lk.customers.get(r.getCustomerId()));
            d.setPartner(lk.partners.get(r.getChannelPartnerId()));
            d.setRemarks(r.getRemarks());
            d.setPaidBy(r.getPaidBy() == null ? null : lk.names.get(r.getPaidBy()));
            d.setAudit(auditInfoMapper.of(r, lk.names));
        }
        return d;
    }
}
