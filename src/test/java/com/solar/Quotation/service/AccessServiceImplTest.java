package com.solar.Quotation.service;

import com.solar.Quotation.Enum.DataScope;
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
import com.solar.Quotation.service.serviceImpl.AccessServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AccessServiceImplTest {

    RoleRepository roleRepository = mock(RoleRepository.class);
    ModuleRepository moduleRepository = mock(ModuleRepository.class);
    PermissionRepository permissionRepository = mock(PermissionRepository.class);
    PermissionCache cache = mock(PermissionCache.class);
    AccessServiceImpl service = new AccessServiceImpl(roleRepository, moduleRepository, permissionRepository, cache);
    MockedStatic<TenantContext> tenant;

    @BeforeEach
    void setUp() {
        tenant = mockStatic(TenantContext.class);
        when(moduleRepository.findByDeletedFalseOrderBySortOrderAsc()).thenReturn(List.of(
                module(1, "QUOTATION", false), module(2, "PRODUCT", false), module(3, "AUDIT", true)));
        when(permissionRepository.findByDeletedFalseOrderByIdAsc()).thenReturn(List.of(
                perm(1, "QUOTATION", "VIEW"), perm(1, "QUOTATION", "CREATE"),
                perm(2, "PRODUCT", "VIEW"), perm(3, "AUDIT", "VIEW")));
    }

    @AfterEach
    void tearDown() {
        tenant.close();
    }

    private void actingAs(String roleCode, Set<String> granted) {
        CurrentUser u = mock(CurrentUser.class);
        when(u.getRoleId()).thenReturn(7L);
        tenant.when(TenantContext::get).thenReturn(u);
        Role r = new Role();
        ReflectionTestUtils.setField(r, "id", 7L);
        r.setCode(roleCode);
        r.setDataScope(DataScope.PARTNER);
        when(roleRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(r));
        when(cache.permissionsForRole(7L)).thenReturn(granted);
    }

    @Test
    void returnsOnlyGrantedModulesAndActions() {
        actingAs("CHANNEL_PARTNER_SALES", Set.of("QUOTATION:VIEW", "QUOTATION:CREATE"));
        AccessResponseDto out = service.currentAccess();
        assertThat(out.getModules()).extracting(ModuleResponseDto::getCode).containsExactly("QUOTATION");
        assertThat(out.getModules().get(0).getActions()).containsExactly("VIEW", "CREATE");
        assertThat(out.getPermissions()).containsExactly("QUOTATION:VIEW", "QUOTATION:CREATE");
    }

    @Test
    void nonSuperAdminNeverGetsPlatformOnlyModulesEvenIfTheDatabaseGrantsThem() {
        actingAs("CHANNEL_PARTNER_ADMIN", Set.of("QUOTATION:VIEW", "AUDIT:VIEW"));
        AccessResponseDto out = service.currentAccess();
        assertThat(out.getModules()).extracting(ModuleResponseDto::getCode).containsExactly("QUOTATION");
        assertThat(out.getPermissions()).doesNotContain("AUDIT:VIEW");
    }

    @Test
    void superAdminGetsPlatformOnlyModules() {
        actingAs("SUPER_ADMIN", Set.of("QUOTATION:VIEW", "AUDIT:VIEW"));
        assertThat(service.currentAccess().getModules())
                .extracting(ModuleResponseDto::getCode).containsExactly("QUOTATION", "AUDIT");
    }

    @Test
    void unauthenticatedAndInactiveRoleAreRefused() {
        tenant.when(TenantContext::get).thenReturn(null);
        assertThatThrownBy(() -> service.currentAccess()).isInstanceOf(ForbiddenException.class);

        CurrentUser u = mock(CurrentUser.class);
        when(u.getRoleId()).thenReturn(99L);
        tenant.when(TenantContext::get).thenReturn(u);
        when(roleRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.currentAccess()).isInstanceOf(ForbiddenException.class);
    }

    private static AppModule module(long id, String code, boolean platformOnly) {
        AppModule m = new AppModule();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCode(code); m.setName(code); m.setPlatformOnly(platformOnly); m.setSortOrder((int) id);
        return m;
    }

    private static Permission perm(long moduleId, String moduleCode, String action) {
        Permission p = new Permission();
        p.setModuleId(moduleId); p.setAction(action); p.setCode(moduleCode + ":" + action);
        return p;
    }
}