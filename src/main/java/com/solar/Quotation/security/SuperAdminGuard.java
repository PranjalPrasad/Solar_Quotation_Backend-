package com.solar.Quotation.security;

import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hard rule independent of the permission matrix: role management is Super Admin only.
 * Even if ROLE:EDIT is granted to another role by mistake (or by old data), the service layer still refuses.
 */
@Component
public class SuperAdminGuard {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final Logger log = LoggerFactory.getLogger(SuperAdminGuard.class);

    private final RoleRepository roleRepository;

    public SuperAdminGuard(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public boolean isSuperAdmin() {
        CurrentUser u = TenantContext.get();
        if (u == null || u.getRoleId() == null) return false;
        return roleRepository.findByIdAndDeletedFalse(u.getRoleId())
                .map(r -> SUPER_ADMIN.equals(r.getCode()))
                .orElse(false);
    }

    public void requireSuperAdmin(String operation) {
        if (isSuperAdmin()) return;
        log.warn("[AUTHZ] denied userId={} permission={} path={}",
                Actors.userId(), "SUPER_ADMIN_ONLY:" + operation, Actors.requestPath());
        throw new ForbiddenException("FORBIDDEN", "Only Super Admin can perform this action");
    }
}