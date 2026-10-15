package com.solar.Quotation.security;

import com.solar.Quotation.exception.ForbiddenException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {

    private final PermissionCache cache;

    public PermissionAspect(PermissionCache cache) {
        this.cache = cache;
    }

    @Before("@annotation(rp)")
    public void check(RequirePermission rp) {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        if (!cache.permissionsForRole(u.getRoleId()).contains(rp.value())) {
            throw new ForbiddenException("FORBIDDEN", "Missing permission " + rp.value());
        }
    }
}
