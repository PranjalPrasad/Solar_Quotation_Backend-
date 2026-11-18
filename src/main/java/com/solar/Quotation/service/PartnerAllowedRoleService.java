package com.solar.Quotation.service;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.AllowedRolesRequestDto;
import com.solar.Quotation.dto.responsedto.AllowedRolesResponseDto;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.PartnerAllowedRole;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.PartnerAllowedRoleRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.util.DiffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PartnerAllowedRoleService {

    private static final Logger log = LoggerFactory.getLogger(PartnerAllowedRoleService.class);
    private static final String PARTNER_ADMIN = "CHANNEL_PARTNER_ADMIN";
    /** Apne platform role ka code yahan set karo; ye kabhi partner ko assign nahi hota. */
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final PartnerAllowedRoleRepository repo;
    private final RoleRepository roleRepo;
    private final ChannelPartnerRepository partnerRepo;
    private final AuditService audit;

    public PartnerAllowedRoleService(PartnerAllowedRoleRepository repo, RoleRepository roleRepo,
                                     ChannelPartnerRepository partnerRepo, AuditService audit) {
        this.repo = repo;
        this.roleRepo = roleRepo;
        this.partnerRepo = partnerRepo;
        this.audit = audit;
    }

    private List<Role> assignable() {
        return roleRepo.findByDeletedFalseOrderByIdAsc().stream()
                .filter(r -> r.getStatus() == RecordStatus.ACTIVE && !SUPER_ADMIN.equals(r.getCode()))
                .toList();
    }

    private ChannelPartner partner(Long id) {
        return partnerRepo.findByIdAndDeletedFalse(id)
                .filter(p -> p.getPartnerType() == PartnerType.PARTNER)
                .orElseThrow(() -> new ResourceNotFoundException("Channel partner not found"));
    }

    private Role adminRole() {
        return roleRepo.findByCodeAndDeletedFalse(PARTNER_ADMIN)
                .orElseThrow(() -> new BusinessException("BUSINESS_RULE", "Role " + PARTNER_ADMIN + " is missing"));
    }

    @Transactional(readOnly = true)
    public AllowedRolesResponseDto get(Long partnerId) {
        partner(partnerId);
        Set<Long> allowed = new HashSet<>(repo.findRoleIds(partnerId));
        AllowedRolesResponseDto out = new AllowedRolesResponseDto();
        out.setPartnerId(partnerId);
        out.setRoles(assignable().stream()
                .map(r -> new AllowedRolesResponseDto.Item(r.getId(), r.getCode(), r.getName(),
                        allowed.contains(r.getId()), PARTNER_ADMIN.equals(r.getCode())))
                .toList());
        return out;
    }

    @Transactional
    public AllowedRolesResponseDto replace(Long partnerId, AllowedRolesRequestDto req) {
        ChannelPartner p = partner(partnerId);
        Map<Long, Role> byId = assignable().stream().collect(Collectors.toMap(Role::getId, r -> r));

        Set<Long> wanted = new LinkedHashSet<>(req.getRoleIds());
        for (Long id : wanted) {
            if (!byId.containsKey(id)) {
                throw new BusinessException("VALIDATION_ERROR", "Role " + id + " cannot be assigned to a partner");
            }
        }
        wanted.add(adminRole().getId()); // partner hamesha apna admin rakh sake

        Set<Long> before = new TreeSet<>(repo.findRoleIds(partnerId));
        for (Long removed : before) {
            if (!wanted.contains(removed) && repo.countUsersWithRoleInPartner(partnerId, removed) > 0) {
                throw new BusinessException("BUSINESS_RULE",
                        "Role is still assigned to users of this partner; reassign them first");
            }
        }

        repo.deleteByPartner(partnerId);
        for (Long rid : wanted) {
            repo.save(new PartnerAllowedRole(partnerId, rid));
        }

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("allowedRoleIds", before);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("allowedRoleIds", new TreeSet<>(wanted));
        audit.record(AuditAction.UPDATE, "CHANNEL_PARTNER", "PartnerAllowedRole",
                partnerId, p.getCode(), partnerId, b, a);

        CurrentUser u = TenantContext.get();
        log.info("[PARTNER] allowed-roles changed partnerId={} actor={} before={} after={}",
                partnerId, u == null ? null : u.getUserId(), before, new TreeSet<>(wanted));
        return get(partnerId);
    }

    /** Naye partner ka default: sirf admin role. */
    @Transactional
    public void seedDefault(Long partnerId, Long adminRoleId) {
        repo.save(new PartnerAllowedRole(partnerId, adminRoleId));
    }

    /** UserServiceImpl.create/update me partner user save karne se pehle call karo. */
    @Transactional(readOnly = true)
    public void assertAllowed(Long partnerId, Long roleId) {
        if (!repo.existsByPartnerIdAndRoleId(partnerId, roleId)) {
            throw new BusinessException("BUSINESS_RULE", "This role is not enabled for the partner");
        }
    }
}