package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.RolePermissionUpdateRequestDto;
import com.solar.Quotation.dto.requestdto.RoleRequestDto;
import com.solar.Quotation.dto.responsedto.ModuleResponseDto;
import com.solar.Quotation.dto.responsedto.PermissionMatrixResponseDto;
import com.solar.Quotation.dto.responsedto.RoleResponseDto;
import com.solar.Quotation.entity.AppModule;
import com.solar.Quotation.entity.Permission;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.entity.RolePermission;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.ModuleRepository;
import com.solar.Quotation.repository.PermissionRepository;
import com.solar.Quotation.repository.RolePermissionRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.RoleService;
import com.solar.Quotation.util.DiffUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RoleServiceImpl implements RoleService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionCache permissionCache;
    private final AuditService auditService;

    public RoleServiceImpl(RoleRepository roleRepository, ModuleRepository moduleRepository,
                           PermissionRepository permissionRepository,
                           RolePermissionRepository rolePermissionRepository,
                           PermissionCache permissionCache, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionCache = permissionCache;
        this.auditService = auditService;
    }

    // ---------------- roles ----------------

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponseDto> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : roleRepository.countUsersGroupedByRole()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return roleRepository.findByDeletedFalseOrderByIdAsc().stream()
                .map(r -> toResponse(r, counts.getOrDefault(r.getId(), 0L))).toList();
    }

    @Override
    @Transactional
    public RoleResponseDto create(RoleRequestDto req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Role code is required");
        }
        String code = req.getCode().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByCodeAndDeletedFalse(code)) {
            throw new BusinessException("DUPLICATE", "Role code already exists", HttpStatus.CONFLICT);
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(req.getName().trim());
        role.setDescription(req.getDescription());
        role.setDataScope(parseScope(req.getDataScope(), DataScope.PARTNER));
        role.setSystem(false);
        role.setStatus(RecordStatus.ACTIVE);
        Role saved = roleRepository.save(role);
        auditService.record(AuditAction.CREATE, "ROLE", "Role", saved.getId(), saved.getCode(),
                actorPartnerId(), null);
        return toResponse(saved, 0);
    }

    @Override
    @Transactional
    public RoleResponseDto update(Long id, RoleRequestDto req) {
        Role role = findRole(id);
        guardNotSuperAdmin(role);
        Map<String, Object> before = snapshot(role);
        role.setName(req.getName().trim());
        role.setDescription(req.getDescription());
        if (req.getDataScope() != null && !req.getDataScope().isBlank()) {
            role.setDataScope(parseScope(req.getDataScope(), role.getDataScope()));
        }
        Role saved = roleRepository.save(role);
        auditService.record(AuditAction.UPDATE, "ROLE", "Role", saved.getId(), saved.getCode(),
                actorPartnerId(), DiffUtil.diff(before, snapshot(saved)));
        return toResponse(saved, roleRepository.countUsersByRoleId(saved.getId()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Role role = findRole(id);
        if (role.isSystem()) {
            throw new BusinessException("BUSINESS_RULE", "System roles cannot be deleted");
        }
        if (roleRepository.countUsersByRoleId(id) > 0) {
            throw new BusinessException("BUSINESS_RULE", "Role is assigned to users. Reassign them first.");
        }
        role.setDeleted(true);
        roleRepository.save(role);
        rolePermissionRepository.deleteByRoleId(id);
        permissionCache.evict(id);
        auditService.record(AuditAction.DELETE, "ROLE", "Role", role.getId(), role.getCode(),
                actorPartnerId(), null);
    }

    // ---------------- modules and matrix ----------------

    @Override
    @Transactional(readOnly = true)
    public List<ModuleResponseDto> modules() {
        return buildModuleDtos(moduleRepository.findByDeletedFalseOrderBySortOrderAsc(),
                permissionRepository.findByDeletedFalseOrderByIdAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionMatrixResponseDto matrix() {
        List<AppModule> modules = moduleRepository.findByDeletedFalseOrderBySortOrderAsc();
        List<Permission> perms = permissionRepository.findByDeletedFalseOrderByIdAsc();
        PermissionMatrixResponseDto out = new PermissionMatrixResponseDto();
        out.setModules(buildModuleDtos(modules, perms));
        List<PermissionMatrixResponseDto.RoleGrant> rows = new ArrayList<>();
        for (Role r : roleRepository.findByDeletedFalseOrderByIdAsc()) {
            rows.add(buildGrant(r, modules, perms));
        }
        out.setRoles(rows);
        return out;
    }

    @Override
    @Transactional
    public PermissionMatrixResponseDto.RoleGrant updatePermissions(Long id, RolePermissionUpdateRequestDto req) {
        Role role = findRole(id);
        guardNotSuperAdmin(role);

        List<AppModule> modules = moduleRepository.findByDeletedFalseOrderBySortOrderAsc();
        List<Permission> perms = permissionRepository.findByDeletedFalseOrderByIdAsc();
        Map<String, AppModule> moduleByCode = new HashMap<>();
        for (AppModule m : modules) moduleByCode.put(m.getCode(), m);
        Map<String, Permission> permByCode = new HashMap<>();
        for (Permission p : perms) permByCode.put(p.getCode(), p);

        Map<String, Object> before = grantedSnapshot(modules, perms,
                new LinkedHashSet<>(rolePermissionRepository.findPermissionCodesByRoleId(id)));

        // validate the whole request first, so nothing is half-saved
        Set<String> newCodes = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> e : req.getGranted().entrySet()) {
            String moduleCode = e.getKey() == null ? "" : e.getKey().trim().toUpperCase(Locale.ROOT);
            AppModule m = moduleByCode.get(moduleCode);
            if (m == null) {
                throw new BusinessException("VALIDATION_ERROR", "Unknown module " + e.getKey());
            }
            List<String> actions = e.getValue() == null ? List.of() : e.getValue();
            if (m.isPlatformOnly() && !actions.isEmpty()) {
                throw new BusinessException("PLATFORM_ONLY_MODULE",
                        m.getCode() + " is a platform-only module and cannot be granted to this role");
            }
            for (String action : actions) {
                String code = moduleCode + ":" + action.trim().toUpperCase(Locale.ROOT);
                if (!permByCode.containsKey(code)) {
                    throw new BusinessException("VALIDATION_ERROR", "Action " + action + " is not available for " + moduleCode);
                }
                newCodes.add(code);
            }
        }

        rolePermissionRepository.deleteByRoleId(id);
        List<RolePermission> rows = new ArrayList<>();
        for (String code : newCodes) {
            rows.add(new RolePermission(id, permByCode.get(code).getId()));
        }
        rolePermissionRepository.saveAll(rows);
        permissionCache.evict(id);

        Map<String, Object> after = grantedSnapshot(modules, perms, newCodes);
        auditService.record(AuditAction.PERMISSION_CHANGE, "ROLE", "Role", role.getId(), role.getCode(),
                actorPartnerId(), DiffUtil.diff(before, after));
        return buildGrant(role, modules, perms);
    }

    // ---------------- helpers ----------------

    private Role findRole(Long id) {
        return roleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private void guardNotSuperAdmin(Role role) {
        if (SUPER_ADMIN.equals(role.getCode())) {
            throw new BusinessException("BUSINESS_RULE", "SUPER_ADMIN always has full access and cannot be edited");
        }
    }

    private DataScope parseScope(String value, DataScope fallback) {
        if (value == null || value.isBlank()) return fallback;
        DataScope scope = DataScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        if (scope == DataScope.ALL) {
            throw new BusinessException("BUSINESS_RULE", "Data scope ALL is reserved for SUPER_ADMIN");
        }
        return scope;
    }

    private Long actorPartnerId() {
        return TenantContext.get().getPartnerId();
    }

    private Map<String, Object> snapshot(Role r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("dataScope", r.getDataScope() == null ? null : r.getDataScope().name());
        return m;
    }

    /** module code to "VIEW,CREATE" (catalog order). Every module is present so the diff is clean. */
    private Map<String, Object> grantedSnapshot(List<AppModule> modules, List<Permission> perms, Set<String> codes) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : grantedMap(modules, perms, codes).entrySet()) {
            m.put(e.getKey(), String.join(",", e.getValue()));
        }
        return m;
    }

    private Map<String, List<String>> grantedMap(List<AppModule> modules, List<Permission> perms, Set<String> codes) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (AppModule m : modules) {
            List<String> actions = new ArrayList<>();
            for (Permission p : perms) {
                if (p.getModuleId().equals(m.getId()) && codes.contains(p.getCode())) {
                    actions.add(p.getAction());
                }
            }
            out.put(m.getCode(), actions);
        }
        return out;
    }

    private PermissionMatrixResponseDto.RoleGrant buildGrant(Role r, List<AppModule> modules, List<Permission> perms) {
        Set<String> codes = new LinkedHashSet<>(rolePermissionRepository.findPermissionCodesByRoleId(r.getId()));
        Map<String, List<String>> granted = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : grantedMap(modules, perms, codes).entrySet()) {
            if (!e.getValue().isEmpty()) granted.put(e.getKey(), e.getValue());
        }
        PermissionMatrixResponseDto.RoleGrant g = new PermissionMatrixResponseDto.RoleGrant();
        g.setId(r.getId());
        g.setCode(r.getCode());
        g.setName(r.getName());
        g.setSystem(r.isSystem());
        g.setGranted(granted);
        return g;
    }

    private List<ModuleResponseDto> buildModuleDtos(List<AppModule> modules, List<Permission> perms) {
        List<ModuleResponseDto> out = new ArrayList<>();
        for (AppModule m : modules) {
            ModuleResponseDto d = new ModuleResponseDto();
            d.setCode(m.getCode());
            d.setName(m.getName());
            d.setRoute(m.getRoute());
            d.setPlatformOnly(m.isPlatformOnly());
            d.setSortOrder(m.getSortOrder());
            List<String> actions = new ArrayList<>();
            for (Permission p : perms) {
                if (p.getModuleId().equals(m.getId())) actions.add(p.getAction());
            }
            d.setActions(actions);
            out.add(d);
        }
        return out;
    }

    private RoleResponseDto toResponse(Role r, long userCount) {
        RoleResponseDto d = new RoleResponseDto();
        d.setId(r.getId());
        d.setCode(r.getCode());
        d.setName(r.getName());
        d.setDescription(r.getDescription());
        d.setDataScope(r.getDataScope().name());
        d.setSystem(r.isSystem());
        d.setStatus(r.getStatus().name());
        d.setUserCount(userCount);
        d.setCreatedAt(r.getCreatedAt());
        d.setUpdatedAt(r.getUpdatedAt());
        return d;
    }
}