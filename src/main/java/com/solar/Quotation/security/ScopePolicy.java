package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule: only the SUPER_ADMIN role may have data scope ALL.
 * Enforced in three places: JwtAuthFilter (every request), and RoleService create/update (call assertAllowed),
 * and data.sql (seed must not give ALL to any other role).
 */
public final class ScopePolicy {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final Logger log = LoggerFactory.getLogger(ScopePolicy.class);

    private ScopePolicy() { }

    public static boolean isAllowed(String roleCode, DataScope scope) {
        return scope != DataScope.ALL || SUPER_ADMIN.equals(roleCode);
    }

    public static void assertAllowed(String roleCode, DataScope scope) {
        if (!isAllowed(roleCode, scope)) {
            log.warn("[ROLE] rejected data scope ALL for role={}", roleCode);
            throw new BusinessException("VALIDATION_ERROR", "Only the SUPER_ADMIN role can have data scope ALL");
        }
    }
}