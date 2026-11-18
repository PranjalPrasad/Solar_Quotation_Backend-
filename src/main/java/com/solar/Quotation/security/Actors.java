package com.solar.Quotation.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Small helpers so [ROLE] and [AUTHZ] log lines all describe the actor and the request the same way.
 * ASSUMPTION: CurrentUser exposes getUserId(). If your getter is named differently (getId()), change it here only.
 */
public final class Actors {

    private Actors() { }

    public static Long userId() {
        CurrentUser u = TenantContext.get();
        return u == null ? null : u.getUserId();
    }

    /** e.g. "userId=3 roleId=1 partnerId=1" (ids only, no names or emails). */
    public static String describe() {
        CurrentUser u = TenantContext.get();
        if (u == null) return "anonymous";
        return "userId=" + u.getUserId() + " roleId=" + u.getRoleId() + " partnerId=" + u.getPartnerId();
    }

    /** e.g. "PUT /api/roles/5/permissions" (no query string, CR/LF stripped so a request cannot forge log lines). */
    public static String requestPath() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest r = attrs.getRequest();
                return (r.getMethod() + " " + r.getRequestURI()).replaceAll("[\\r\\n]", "_");
            }
        } catch (RuntimeException ignored) { /* logging must never break a request */ }
        return "n/a";
    }
}