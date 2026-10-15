package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.ChannelPartnerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.UserFilterRequestDto;
import com.solar.Quotation.dto.requestdto.UserRequestDto;
import com.solar.Quotation.dto.responsedto.ChannelPartnerResponseDto;
import com.solar.Quotation.dto.responsedto.ChannelPartnerStatsResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerOptionResponseDto;
import com.solar.Quotation.dto.responsedto.UserResponseDto;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.ChannelPartnerService;
import com.solar.Quotation.service.UserService;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.DiffUtil;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ChannelPartnerServiceImpl implements ChannelPartnerService {

    /** The built-in PLATFORM tenant (created by data.sql). Partner codes are counted under it. */
    private static final Long PLATFORM_PARTNER_ID = 1L;
    private static final String PARTNER_ADMIN = "CHANNEL_PARTNER_ADMIN";
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "name", "code", "status");

    private final ChannelPartnerRepository partnerRepository;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;

    public ChannelPartnerServiceImpl(ChannelPartnerRepository partnerRepository, AppUserRepository userRepository,
                                     RoleRepository roleRepository, UserService userService,
                                     AuditService auditService, CodeGenerator codeGenerator) {
        this.partnerRepository = partnerRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
    }

    // ---------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChannelPartnerResponseDto> list(ChannelPartnerFilterRequestDto f) {
        Page<ChannelPartner> page = partnerRepository.search(PartnerType.PARTNER, parseStatus(f.getStatus()),
                f.getSearchOrNull(), f.toPageable("createdAt,desc", SORT_FIELDS));

        // one grouped query for the user counts of the whole page
        Map<Long, Long> userCounts = new HashMap<>();
        List<Long> ids = page.getContent().stream().map(ChannelPartner::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : userRepository.countGroupedByPartner(ids)) {
                userCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        return PageResponse.of(page, p -> toResponse(p, statsWithUsers(userCounts.getOrDefault(p.getId(), 0L))));
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
        p.setStatus(parseStatus(req.getStatus()) == null ? RecordStatus.ACTIVE : parseStatus(req.getStatus()));
        ChannelPartner saved = partnerRepository.save(p);

        auditService.record(AuditAction.CREATE, "CHANNEL_PARTNER", "ChannelPartner", saved.getId(), saved.getCode(),
                saved.getId(), DiffUtil.diff(null, snapshot(saved)));

        // first admin user of the partner (password is generated; see UserResponseDto.temporaryPassword)
        UserRequestDto admin = new UserRequestDto();
        admin.setName(req.getAdminUser().getName());
        admin.setEmail(req.getAdminUser().getEmail());
        admin.setUsername(req.getAdminUser().getUsername());
        admin.setPhone(req.getAdminUser().getPhone());
        admin.setRoleId(adminRole.getId());
        admin.setPartnerId(saved.getId());
        admin.setStatus("ACTIVE");
        UserResponseDto createdAdmin = userService.create(admin);

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
        applyFields(p, req);
        RecordStatus newStatus = parseStatus(req.getStatus());
        if (newStatus != null) {
            p.setStatus(newStatus);
        }
        ChannelPartner saved = partnerRepository.save(p);

        auditService.record(AuditAction.UPDATE, "CHANNEL_PARTNER", "ChannelPartner", saved.getId(), saved.getCode(),
                saved.getId(), DiffUtil.diff(before, snapshot(saved)));
        return toResponse(saved, statsFor(saved.getId()));
    }

    @Override
    @Transactional
    public ChannelPartnerResponseDto updateStatus(Long id, ChannelPartnerStatusRequestDto req) {
        ChannelPartner p = findPartner(id);
        Map<String, Object> before = snapshot(p);
        p.setStatus(RecordStatus.valueOf(req.getStatus()));
        ChannelPartner saved = partnerRepository.save(p);

        // Every user of an inactive partner is blocked by JwtAuthFilter on the very next request.
        auditService.record(AuditAction.STATUS_CHANGE, "CHANNEL_PARTNER", "ChannelPartner", saved.getId(),
                saved.getCode(), saved.getId(), DiffUtil.diff(before, snapshot(saved)));
        return toResponse(saved, statsFor(saved.getId()));
    }

    // ---------------------------------------------------------------- helpers

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
        if (value == null || value.isBlank()) {
            return null;
        }
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

    /** users is live. Add customers, projects, quotations and referral totals here when those modules exist. */
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
