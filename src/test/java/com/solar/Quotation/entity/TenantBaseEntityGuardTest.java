package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** L3 guard: the entity itself refuses writes outside the caller's own tenant. */
class TenantBaseEntityGuardTest {

    private static final Long PLATFORM = 1L, PARTNER_A = 12L, OTHER = 99L;

    static class Rec extends TenantBaseEntity { }
    static class ManagedRec extends TenantBaseEntity {            // e.g. AppUser, which Super Admin must manage
        @Override protected boolean platformMayWriteAcrossTenants() { return true; }
    }

    @AfterEach
    void cleanUp() { TenantContext.clear(); }

    private void login(DataScope scope, Long partnerId) {
        String role = scope == DataScope.ALL ? "SUPER_ADMIN" : "ROLE_X";
        TenantContext.set(new CurrentUser(5L, "Tester", 10L, role, scope, partnerId));
    }

    private Rec rec(Long partnerId) { Rec r = new Rec(); r.setChannelPartnerId(partnerId); return r; }

    @Test
    void partnerCanWriteOwnRows_notOthers() {
        login(DataScope.PARTNER, PARTNER_A);
        assertDoesNotThrow(() -> rec(PARTNER_A).guardTenant());
        assertThrows(ForbiddenException.class, () -> rec(OTHER).guardTenant());
    }

    @Test
    void superAdminCanWritePlatformRows_notPartnerRows() {
        login(DataScope.ALL, PLATFORM);
        assertDoesNotThrow(() -> rec(PLATFORM).guardTenant());
        assertThrows(ForbiddenException.class, () -> rec(PARTNER_A).guardTenant());
    }

    @Test
    void managedEntityKeepsSuperAdminAbleToOnboardPartnerUsers() {
        login(DataScope.ALL, PLATFORM);
        ManagedRec m = new ManagedRec();
        m.setChannelPartnerId(PARTNER_A);
        assertDoesNotThrow(m::guardTenant);
    }

    @Test
    void codeWithNoSignedInUserIsNotChecked() {
        assertDoesNotThrow(() -> rec(OTHER).guardTenant());
    }
}