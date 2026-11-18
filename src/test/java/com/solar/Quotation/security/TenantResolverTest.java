package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit test of the tenant rules (no Spring, no database). */
class TenantResolverTest {

    private static final Long PLATFORM = 1L;
    private static final Long PARTNER_A = 12L;
    private static final Long OTHER = 99L;

    private final TenantResolver resolver = new TenantResolver();

    @AfterEach
    void cleanUp() { TenantContext.clear(); }

    private CurrentUser login(DataScope scope, Long partnerId) {
        String role = scope == DataScope.ALL ? "SUPER_ADMIN" : "ROLE_X";
        CurrentUser u = new CurrentUser(5L, "Tester", 10L, role, scope, partnerId);
        TenantContext.set(u);
        return u;
    }

    /** What JwtAuthFilter does for a GET with ?viewPartnerId= */
    private void getRequestViewing(CurrentUser u, Long view) {
        u.setReadRequest(true);
        u.setViewPartnerId(view);
    }

    // ---- writes
    @Test
    void superAdminWritesAreAlwaysUnderPlatform_neverNull_neverTheClientValue() {
        login(DataScope.ALL, PLATFORM);
        assertEquals(PLATFORM, resolver.effectivePartnerId(null));
        assertEquals(PLATFORM, resolver.effectivePartnerId(OTHER));
        assertEquals(PLATFORM, resolver.requirePartnerIdForCreate(null));
        assertEquals(PLATFORM, resolver.requirePartnerIdForCreate(OTHER));
    }

    @Test
    void writesStayOwnTenantEvenWhileSuperAdminIsViewingAPartner() {
        CurrentUser u = login(DataScope.ALL, PLATFORM);
        getRequestViewing(u, OTHER);
        assertEquals(PLATFORM, resolver.effectivePartnerId());
        assertEquals(PLATFORM, resolver.requirePartnerIdForCreate(OTHER));
    }

    @Test
    void partnerUserWritesUnderOwnPartner_andCannotNameAnother() {
        login(DataScope.PARTNER, PARTNER_A);
        assertEquals(PARTNER_A, resolver.effectivePartnerId(OTHER));
        assertEquals(PARTNER_A, resolver.requirePartnerIdForCreate(OTHER));
    }

    // ---- reads
    @Test
    void superAdminReadDefaultsToPlatform_andCanViewOnePartner() {
        CurrentUser u = login(DataScope.ALL, PLATFORM);
        u.setReadRequest(true);
        assertEquals(PLATFORM, resolver.effectiveReadPartnerId());
        assertEquals(PLATFORM, resolver.effectiveReadPartnerId(null));
        assertEquals(7L, resolver.effectiveReadPartnerId(7L));
        u.setViewPartnerId(7L);
        assertEquals(7L, resolver.effectiveReadPartnerId());
    }

    @Test
    void viewIsIgnoredOnAnyNonReadRequest() {
        CurrentUser u = login(DataScope.ALL, PLATFORM);
        u.setReadRequest(false);
        u.setViewPartnerId(7L);
        assertEquals(PLATFORM, resolver.effectiveReadPartnerId());
        assertEquals(PLATFORM, resolver.effectiveReadPartnerId(7L));
    }

    @Test
    void partnerAndOwnScopeUsersCannotViewAnotherPartner() {
        for (DataScope scope : new DataScope[]{DataScope.PARTNER, DataScope.OWN}) {
            CurrentUser u = login(scope, PARTNER_A);
            getRequestViewing(u, OTHER);
            assertEquals(PARTNER_A, resolver.effectiveReadPartnerId());
            assertEquals(PARTNER_A, resolver.effectiveReadPartnerId(OTHER));
        }
    }

    // ---- guards
    @Test
    void assertWritable_otherTenantRecordLooksMissing() {
        login(DataScope.ALL, PLATFORM);
        assertThrows(ResourceNotFoundException.class, () -> resolver.assertWritable(PARTNER_A, "Customer", 3L));
        assertDoesNotThrow(() -> resolver.assertWritable(PLATFORM, "Customer", 3L));
    }

    @Test
    void ownerFilter_onlyForOwnScope() {
        login(DataScope.OWN, PARTNER_A);
        assertEquals(5L, resolver.ownerFilter());
        login(DataScope.PARTNER, PARTNER_A);
        assertNull(resolver.ownerFilter());
    }

    @Test
    void noSignedInUser_isRejected() {
        assertThrows(ForbiddenException.class, () -> resolver.effectivePartnerId(null));
        assertThrows(ForbiddenException.class, () -> resolver.effectiveReadPartnerId());
    }

    @Test
    void userWithoutPartner_isRejected() {
        login(DataScope.PARTNER, null);
        assertThrows(ForbiddenException.class, () -> resolver.effectivePartnerId(null));
    }

    // ---- scope policy: only SUPER_ADMIN may have ALL
    @Test
    void onlySuperAdminMayHaveScopeAll() {
        assertTrue(ScopePolicy.isAllowed("SUPER_ADMIN", DataScope.ALL));
        assertFalse(ScopePolicy.isAllowed("PARTNER_ADMIN", DataScope.ALL));
        assertTrue(ScopePolicy.isAllowed("PARTNER_ADMIN", DataScope.PARTNER));
        assertTrue(ScopePolicy.isAllowed("SALES", DataScope.OWN));
        assertThrows(BusinessException.class, () -> ScopePolicy.assertAllowed("PARTNER_ADMIN", DataScope.ALL));
        assertDoesNotThrow(() -> ScopePolicy.assertAllowed("SUPER_ADMIN", DataScope.ALL));
    }
}