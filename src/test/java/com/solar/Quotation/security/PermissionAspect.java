package com.solar.Quotation.security;

import com.solar.Quotation.exception.ForbiddenException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    private final PermissionCache cache;

    public PermissionAspect(PermissionCache cache) {
        this.cache = cache;
    }

    @Before("@annotation(rp)")
    public void check(RequirePermission rp) {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            log.warn("[AUTHZ] denied userId={} permission={} path={}", null, rp.value(), Actors.requestPath());
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        if (!cache.permissionsForRole(u.getRoleId()).contains(rp.value())) {
            log.warn("[AUTHZ] denied userId={} permission={} path={}", u.getUserId(), rp.value(), Actors.requestPath());
            throw new ForbiddenException("FORBIDDEN", "Missing permission " + rp.value());
        }
    }
}