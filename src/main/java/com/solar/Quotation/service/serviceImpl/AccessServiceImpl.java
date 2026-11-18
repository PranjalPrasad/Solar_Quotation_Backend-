package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.dto.responsedto.AccessResponseDto;
import com.solar.Quotation.dto.responsedto.ModuleResponseDto;
import com.solar.Quotation.entity.AppModule;
import com.solar.Quotation.entity.Permission;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.ModuleRepository;
import com.solar.Quotation.repository.PermissionRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.solar.Quotation.security.SuperAdminGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AccessServiceImpl implements AccessService {

    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionCache permissionCache;

    public AccessServiceImpl(RoleRepository roleRepository, ModuleRepository moduleRepository,
                             PermissionRepository permissionRepository, PermissionCache permissionCache) {
        this.roleRepository = roleRepository;
        this.moduleRepository = moduleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionCache = permissionCache;
    }

    @Override
    @Transactional(readOnly = true)
    public AccessResponseDto currentAccess() {
        CurrentUser u = TenantContext.get();
        if (u == null) throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        Role role = roleRepository.findByIdAndDeletedFalse(u.getRoleId())
                .orElseThrow(() -> new ForbiddenException("ROLE_INACTIVE", "Your role is no longer active"));

        boolean superAdmin = SuperAdminGuard.SUPER_ADMIN.equals(role.getCode());
        Set<String> granted = permissionCache.permissionsForRole(role.getId());
        List<Permission> perms = permissionRepository.findByDeletedFalseOrderByIdAsc();

        List<ModuleResponseDto> modules = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (AppModule m : moduleRepository.findByDeletedFalseOrderBySortOrderAsc()) {
            // Defence in depth: a platform-only module never reaches a non Super Admin, whatever the DB says.
            if (m.isPlatformOnly() && !superAdmin) continue;
            List<String> actions = new ArrayList<>();
            for (Permission p : perms) {
                if (p.getModuleId().equals(m.getId()) && granted.contains(p.getCode())) {
                    actions.add(p.getAction());
                    codes.add(p.getCode());
                }
            }
            if (actions.isEmpty()) continue;
            ModuleResponseDto d = new ModuleResponseDto();
            d.setCode(m.getCode());
            d.setName(m.getName());
            d.setRoute(m.getRoute());
            d.setPlatformOnly(m.isPlatformOnly());
            d.setSortOrder(m.getSortOrder());
            d.setActions(actions);
            modules.add(d);
        }

        AccessResponseDto out = new AccessResponseDto();
        out.setRoleCode(role.getCode());
        out.setDataScope(role.getDataScope() == null ? null : role.getDataScope().name());
        out.setModules(modules);
        out.setPermissions(codes);
        return out;
    }
}