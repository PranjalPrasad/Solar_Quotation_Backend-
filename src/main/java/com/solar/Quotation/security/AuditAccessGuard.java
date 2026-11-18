package com.solar.Quotation.security;

import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.service.PlatformTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Who may read the audit log: a user of the PLATFORM tenant whose role holds the permission (AUDIT:VIEW / AUDIT:EXPORT).
 *
 *   - partner users (any other tenant)            -> 403, even if the permission was granted to their role by mistake
 *   - platform staff WITHOUT the permission       -> 403
 *   - Super Admin (holds every permission)        -> allowed
 *
 * Checked inside AuditServiceImpl, so every entry point (screen, export, detail) is covered.
 * It does not depend on @RequirePermission: in this code base that annotation is only honoured by an aspect that
 * lives in src/test (see docs/AUDIT.md, "Finding").
 */
@Component
public class AuditAccessGuard {

    public static final String VIEW = "AUDIT:VIEW";
    public static final String EXPORT = "AUDIT:EXPORT";

    private static final Logger log = LoggerFactory.getLogger(AuditAccessGuard.class);

    private final PermissionCache permissionCache;
    private final PlatformTenant platformTenant;

    public AuditAccessGuard(PermissionCache permissionCache, PlatformTenant platformTenant) {
        this.permissionCache = permissionCache;
        this.platformTenant = platformTenant;
    }

    /** @throws ForbiddenException (HTTP 403) unless the caller is platform staff holding {@code permission}. */
    public void require(String permission) {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        boolean platformUser = u.getPartnerId() != null && u.getPartnerId().equals(platformTenant.id());
        boolean granted = u.getRoleId() != null
                && permissionCache.permissionsForRole(u.getRoleId()).contains(permission);
        if (platformUser && granted) return;
        log.warn("[AUDIT] access denied userId={} permission={} platformUser={} granted={} path={}",
                u.getUserId(), permission, platformUser, granted, Actors.requestPath());
        throw new ForbiddenException("FORBIDDEN", "You do not have permission to view the audit log");
    }
}