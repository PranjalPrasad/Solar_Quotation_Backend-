package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.ChannelPartnerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.ReferralMarkPaidRequestDto;
import com.solar.Quotation.dto.requestdto.UserFilterRequestDto;
import com.solar.Quotation.dto.requestdto.UserRequestDto;
import com.solar.Quotation.dto.responsedto.*;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.exception.ApiException;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PartnerStatusGuard;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.ChannelPartnerService;
import com.solar.Quotation.service.PartnerAllowedRoleService;
import com.solar.Quotation.service.SettingsService;
import com.solar.Quotation.service.UserService;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.DiffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ChannelPartnerServiceImpl implements ChannelPartnerService {

    private static final Logger log = LoggerFactory.getLogger(ChannelPartnerServiceImpl.class);

    /** The built-in PLATFORM tenant (created by data.sql). Partner codes are counted under it. */
    private static final Long PLATFORM_PARTNER_ID = 1L;
    private static final String PARTNER_ADMIN = "CHANNEL_PARTNER_ADMIN";
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "name", "code", "status");

    private final TenantResolver tenantResolver;
    private final QuotationRepository quotationRepo;
    private final ChannelPartnerRepository partnerRepository;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;
    private final SettingsService settingsService;
    private final PartnerAllowedRoleService allowedRoleService;
    private final PartnerStatusGuard statusGuard;

    public ChannelPartnerServiceImpl(TenantResolver tenantResolver,
                                     QuotationRepository quotationRepo,
                                     ChannelPartnerRepository partnerRepository,
                                     AppUserRepository userRepository,
                                     RoleRepository roleRepository,
                                     UserService userService,
                                     AuditService auditService,
                                     CodeGenerator codeGenerator,
                                     SettingsService settingsService,
                                     PartnerAllowedRoleService allowedRoleService,
                                     PartnerStatusGuard statusGuard) {
        this.tenantResolver = tenantResolver;
        this.quotationRepo = quotationRepo;
        this.partnerRepository = partnerRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.settingsService = settingsService;
        this.allowedRoleService = allowedRoleService;
        this.statusGuard = statusGuard;
    }

    // ---------------------------------------------------------------- referrals

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReferralActivityResponseDto> getReferralActivity(
            Long partnerId, String status, Pageable pageable) {

        Long scoped = tenantResolver.effectivePartnerId(partnerId);
        if (scoped == null) throw new ApiException(403, "partnerId required");

        QuotationStatus st = parseQuotationStatus(status);

        Page<Object[]> page = quotationRepo.referralActivity(scoped, st, pageable);

        return PageResponse.of(page.map(r -> {
            ReferralActivityResponseDto d = new ReferralActivityResponseDto();
            d.setQuotationId(((Number) r[0]).longValue());
            d.setQuotationNo((String) r[1]);
            d.setCustomer((String) r[2]);
            d.setQuotationAmount((BigDecimal) r[3]);
            d.setReferralPercent((BigDecimal) r[4]);
            d.setReferralAmount((BigDecimal) r[5]);
            d.setStatus(r[6] == null ? null : r[6].toString());
            d.setDate(toLocalDate(r[7]));
            d.setPaidDate(toLocalDate(r[8]));
            return d;
        }));
    }

    @Override
    @Transactional
    public void markReferralPaid(Long partnerId, Long quotationId, ReferralMarkPaidRequestDto request) {

        Long scoped = tenantResolver.effectivePartnerId(partnerId);
        if (scoped == null) throw new ApiException(403, "partnerId required");

        Quotation q = quotationRepo.findOne(quotationId, scoped, null)
                .orElseThrow(() -> new ApiException(404, "Quotation not found"));

        if (q.getStatus() != QuotationStatus.APPROVED) {
            throw new ApiException(400, "Only APPROVED referrals can be marked PAID");
        }
        if (q.getReferralPaidDate() != null) {
            throw new ApiException(400, "Referral already marked paid");
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", q.getStatus().name());
        before.put("referralPaidDate", q.getReferralPaidDate());

        q.setStatus(QuotationStatus.PAID);
        q.setReferralPaidDate(request.getPaidDate());
        q.setReferralPaymentRef(request.getPaymentRef());
        q.setReferralPaymentRemarks(request.getRemarks());
        quotationRepo.save(q);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", q.getStatus().name());
        after.put("referralPaidDate", q.getReferralPaidDate());
        after.put("referralPaymentRef", q.getReferralPaymentRef());
        after.put("referralPaymentRemarks", q.getReferralPaymentRemarks());

        auditService.record(AuditAction.MARK_PAID,
                "CHANNEL_PARTNER", "Quotation", q.getId(), q.getCode(),
                q.getChannelPartnerId(), before, after);
        log.info("[PARTNER] referral marked paid partnerId={} quotationId={} actor={}", scoped, quotationId, actorId());
    }

    private QuotationStatus parseQuotationStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return QuotationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("VALIDATION_ERROR", "Unknown status " + status);
        }
    }

    private LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        return null;
    }

    // ---------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChannelPartnerResponseDto> list(ChannelPartnerFilterRequestDto f) {
        Page<ChannelPartner> page = partnerRepository.search(PartnerType.PARTNER, parseStatus(f.getStatus()),
                f.getSearchOrNull(), f.toPageable("createdAt,desc", SORT_FIELDS));

        Map<Long, Long> userCounts = new HashMap<>();
        List<Long> ids = page.getContent().stream().map(ChannelPartner::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : userRepository.countGroupedByPartner(ids)) {
                userCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        final Map<Long, Long> counts = userCounts;
        return PageResponse.of(page.map(p ->
                toResponse(p, statsWithUsers(counts.getOrDefault(p.getId(), 0L)))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerOptionResponseDto> options() {
        return partnerRepository.findOptions(PartnerType.PARTNER, RecordStatus.ACTIVE).stream()
                .map(p -> new PartnerOptionResponseDto(p.getId(), p.getCode(), p.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelPartnerResponseDto get(Long id) {
        ChannelPartner p = findPartner(id);
        return toResponse(p, statsFor(p.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelPartnerStatsResponseDto stats(Long id) {
        return statsFor(findPartner(id).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponseDto> users(Long id, UserFilterRequestDto filter) {
        ChannelPartner p = findPartner(id);
        filter.setPartnerId(p.getId());
        return userService.list(filter);
    }

    // ---------------------------------------------------------------- write

    @Override
    @Transactional
    public ChannelPartnerResponseDto create(ChannelPartnerRequestDto req) {
        if (req.getAdminUser() == null) {
            throw new BusinessException("VALIDATION_ERROR", "The first admin user of the partner is required");
        }
        Role adminRole = roleRepository.findByCodeAndDeletedFalse(PARTNER_ADMIN)
                .orElseThrow(() -> new BusinessException("BUSINESS_RULE", "Role " + PARTNER_ADMIN + " is missing"));

        ChannelPartner p = new ChannelPartner();
        p.setCode(codeGenerator.nextPadded("CP", PLATFORM_PARTNER_ID, 4));
        p.setReferralCode("REF-" + p.getCode().replace("-", ""));
        p.setPartnerType(PartnerType.PARTNER);
        applyFields(p, req);
        RecordStatus requested = parseStatus(req.getStatus());
        p.setStatus(requested == null ? RecordStatus.ACTIVE : requested);
        ChannelPartner saved = partnerRepository.save(p);

        // the new partner starts with its own copy of the settings; later changes never cross over
        settingsService.seedForPartner(saved.getId());
        // default allowed roles: sirf partner admin role (Super Admin baad me badha sakta hai)
        allowedRoleService.seedDefault(saved.getId(), adminRole.getId());

        auditService.record(AuditAction.CREATE, "CHANNEL_PARTNER", "ChannelPartner", saved.getId(), saved.getCode(),
                saved.getId(), null, snapshot(saved));

        // username: request me ho to wahi, warna partner code se generate (cp0001.admin)
        String username = blankToNull(req.getAdminUser().getUsername());
        if (username == null) {
            username = saved.getCode().toLowerCase(Locale.ROOT).replace("-", "") + ".admin";
        }

        UserRequestDto admin = new UserRequestDto();
        admin.setName(req.getAdminUser().getName());
        admin.setEmail(req.getAdminUser().getEmail());
        admin.setUsername(username);
        admin.setPhone(req.getAdminUser().getPhone());
        admin.setRoleId(adminRole.getId());
        admin.setPartnerId(saved.getId());
        admin.setStatus("ACTIVE");
        // UserService.create ko temporary password generate karna hai aur must_change_password=true set karna hai
        UserResponseDto createdAdmin = userService.create(admin);

        log.info("[PARTNER] created partnerId={} code={} adminUserId={} actor={}",
                saved.getId(), saved.getCode(), createdAdmin.getId(), actorId());

        ChannelPartnerResponseDto out = toResponse(saved, statsWithUsers(1));
        ChannelPartnerResponseDto.AdminUserInfo info = new ChannelPartnerResponseDto.AdminUserInfo();
        info.setId(createdAdmin.getId());
        info.setCode(createdAdmin.getCode());
        info.setUsername(createdAdmin.getUsername());
        info.setTemporaryPassword(createdAdmin.getTemporaryPassword());
        out.setAdminUser(info);
        return out;
    }

    @Override
    @Transactional
    public ChannelPartnerResponseDto update(Long id, ChannelPartnerRequestDto req) {
        ChannelPartner p = findPartner(id);
        Map<String, Object> before = snapshot(p);
        RecordStatus oldStatus = p.getStatus();
        applyFields(p, req);
        RecordStatus newStatus = parseStatus(req.getStatus());
        if (newStatus != null) {
            p.setStatus(newStatus);
        }
        ChannelPartner saved = partnerRepository.save(p);

        auditService.record(AuditAction.UPDATE, "CHANNEL_PARTNER", "ChannelPartner", saved.getId(), saved.getCode(),
                saved.getId(), before, snapshot(saved));
        log.info("[PARTNER] updated partnerId={} actor={}", saved.getId(), actorId());
        onStatusChanged(saved, oldStatus);
        return toResponse(saved, statsFor(saved.getId()));
    }

    @Override
    @Transactional
    public ChannelPartnerResponseDto updateStatus(Long id, ChannelPartnerStatusRequestDto req) {
        ChannelPartner p = findPartner(id);
        RecordStatus target = parseStatus(req.getStatus());
        if (target == null) {
            throw new BusinessException("VALIDATION_ERROR", "Status is required");
        }
        if (p.getStatus() == target) {
            return toResponse(p, statsFor(p.getId()));   // no-op: audit / log nahi
        }
        Map<String, Object> before = snapshot(p);
        RecordStatus oldStatus = p.getStatus();
        p.setStatus(target);
        ChannelPartner saved = partnerRepository.save(p);

        auditService.record(AuditAction.STATUS_CHANGE, "CHANNEL_PARTNER", "ChannelPartner", saved.getId(),
                saved.getCode(), saved.getId(), before, snapshot(saved));
        onStatusChanged(saved, oldStatus);
        return toResponse(saved, statsFor(saved.getId()));
    }

    // ---------------------------------------------------------------- helpers

    /** Log + cache evict taaki deactivation turant lage. */
    private void onStatusChanged(ChannelPartner saved, RecordStatus oldStatus) {
        if (saved.getStatus() == oldStatus) return;
        statusGuard.evict(saved.getId());
        log.info("[PARTNER] {} partnerId={} actor={}",
                saved.getStatus() == RecordStatus.ACTIVE ? "activated" : "deactivated", saved.getId(), actorId());
    }

    private Long actorId() {
        CurrentUser u = TenantContext.get();
        return u == null ? null : u.getUserId();
    }

    private ChannelPartner findPartner(Long id) {
        return partnerRepository.findByIdAndDeletedFalse(id)
                .filter(p -> p.getPartnerType() == PartnerType.PARTNER)
                .orElseThrow(() -> new ResourceNotFoundException("Channel partner not found"));
    }

    private void applyFields(ChannelPartner p, ChannelPartnerRequestDto req) {
        p.setName(req.getName().trim());
        p.setContactPerson(blankToNull(req.getContactPerson()));
        p.setPhone(blankToNull(req.getPhone()));
        p.setEmail(req.getEmail().trim());
        p.setGstNo(blankToNull(req.getGstNo()));
        p.setAddress(blankToNull(req.getAddress()));
        if (req.getReferralPercent() != null) {
            p.setReferralPercent(req.getReferralPercent());
        }
    }

    private RecordStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return RecordStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("VALIDATION_ERROR", "Unknown status " + value);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private ChannelPartnerStatsResponseDto statsWithUsers(long users) {
        ChannelPartnerStatsResponseDto s = new ChannelPartnerStatsResponseDto();
        s.setUsers(users);
        return s;
    }

    private ChannelPartnerStatsResponseDto statsFor(Long partnerId) {
        return statsWithUsers(userRepository.countByChannelPartnerIdAndDeletedFalse(partnerId));
    }

    private Map<String, Object> snapshot(ChannelPartner p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("contactPerson", p.getContactPerson());
        m.put("phone", p.getPhone());
        m.put("email", p.getEmail());
        m.put("gstNo", p.getGstNo());
        m.put("address", p.getAddress());
        BigDecimal pct = p.getReferralPercent();
        m.put("referralPercent", pct == null ? null : pct.stripTrailingZeros().toPlainString());
        m.put("status", p.getStatus().name());
        return m;
    }

    private ChannelPartnerResponseDto toResponse(ChannelPartner p, ChannelPartnerStatsResponseDto stats) {
        ChannelPartnerResponseDto d = new ChannelPartnerResponseDto();
        d.setId(p.getId());
        d.setCode(p.getCode());
        d.setName(p.getName());
        d.setContactPerson(p.getContactPerson());
        d.setPhone(p.getPhone());
        d.setEmail(p.getEmail());
        d.setGstNo(p.getGstNo());
        d.setAddress(p.getAddress());
        d.setStatus(p.getStatus().name());
        d.setReferralCode(p.getReferralCode());
        d.setReferralPercent(p.getReferralPercent());
        d.setCreatedAt(p.getCreatedAt());
        d.setStats(stats);
        return d;
    }
}