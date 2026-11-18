package com.solar.Quotation.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the dashboard tenant rule without needing a database. Every method of DashboardQueryRepository is either
 *   - a tenant query: takes :partnerId and filters it with plain equality (never "(:partnerId is null or ...)"), or
 *   - a "platform*" query: the cross-partner totals, which only the Super Admin endpoint may call.
 * A new method that is neither fails this test, so an unscoped query cannot slip into a tenant dashboard.
 */
class DashboardQueryScopeContractTest {

    private static List<Method> queryMethods() {
        List<Method> out = new ArrayList<>();
        for (Method m : DashboardQueryRepository.class.getDeclaredMethods()) {
            if (m.getAnnotation(Query.class) != null) out.add(m);
        }
        return out;
    }

    private static String jpql(Method m) {
        return m.getAnnotation(Query.class).value();
    }

    @Test
    void everyMethodHasAQuery_andThereAreSomeToCheck() {
        assertTrue(queryMethods().size() >= 10);
        assertTrue(DashboardQueryRepository.class.getDeclaredMethods().length == queryMethods().size());
    }

    @Test
    void tenantQueries_filterOnAPartnerWithPlainEquality() {
        for (Method m : queryMethods()) {
            if (m.getName().startsWith("platform")) continue;
            String q = jpql(m);
            assertTrue(q.contains("= :partnerId"), m.getName() + " must filter with '= :partnerId'");
            assertFalse(q.contains(":partnerId is null"), m.getName() + " must not accept a null partner (that would mean all tenants)");
        }
    }

    @Test
    void tenantQueries_takeAPartnerIdParameter() {
        for (Method m : queryMethods()) {
            if (m.getName().startsWith("platform")) continue;
            boolean has = false;
            for (var p : m.getParameters()) {
                var ann = p.getAnnotation(org.springframework.data.repository.query.Param.class);
                if (ann != null && "partnerId".equals(ann.value())) has = true;
            }
            assertTrue(has, m.getName() + " must have @Param(\"partnerId\")");
        }
    }

    @Test
    void tenantQueries_excludeDeletedRows() {
        for (Method m : queryMethods()) {
            if (m.getName().startsWith("platform")) continue;
            assertTrue(jpql(m).contains(".deleted = false"), m.getName() + " must exclude soft-deleted rows");
        }
    }

    @Test
    void platformQueries_neverTakeAPartnerId() {
        for (Method m : queryMethods()) {
            if (!m.getName().startsWith("platform")) continue;
            assertFalse(jpql(m).contains(":partnerId"), m.getName() + " is platform wide and must not be filtered by a partner id");
        }
    }

    @Test
    void customerJoin_alsoMatchesThePartner() {
        for (Method m : queryMethods()) {
            String q = jpql(m);
            if (q.contains("join Customer")) {
                assertTrue(q.contains("c.channelPartnerId = q.channelPartnerId"),
                        m.getName() + " must not name a customer of another tenant");
            }
        }
    }
}