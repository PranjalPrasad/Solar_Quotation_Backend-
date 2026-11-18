package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.dto.requestdto.RolePermissionUpdateRequestDto;
import com.solar.Quotation.dto.requestdto.RoleRequestDto;
import com.solar.Quotation.entity.AppModule;
import com.solar.Quotation.entity.Permission;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.entity.RolePermission;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.ModuleRepository;
import com.solar.Quotation.repository.PermissionRepository;
import com.solar.Quotation.repository.RolePermissionRepository;
import com.solar.Quotation.repository.RoleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.util.DiffUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ASSUMPTIONS (adjust if your classes differ): BaseEntity has a field named "id"; the DTOs have setters;
 * CurrentUser has getUserId()/getRoleId()/getPartnerId().
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleServiceImplTest {

    @Mock RoleRepository roleRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock PermissionCache permissionCache;
    @Mock
    AuditService auditService;
    @Mock SuperAdminGuard guard;

    RoleServiceImpl service;
    MockedStatic<TenantContext> tenant;

    static final long ROLE_ID = 5L;

    @BeforeEach
    void setUp() {
        service = new RoleServiceImpl(roleRepository, moduleRepository, permissionRepository,
                rolePermissionRepository, permissionCache, auditService, guard);
        CurrentUser actor = mock(CurrentUser.class);
        when(actor.getUserId()).thenReturn(1L);
        when(actor.getRoleId()).thenReturn(1L);
        when(actor.getPartnerId()).thenReturn(1L);
        tenant = mockStatic(TenantContext.class);
        tenant.when(TenantContext::get).thenReturn(actor);

        // catalog: QUOTATION (VIEW, CREATE, EDIT), PRODUCT (VIEW), platform-only CHANNEL_PARTNER, AUDIT, ROLE
        List<AppModule> modules = List.of(
                module(1, "QUOTATION", false), module(2, "PRODUCT", false),
                module(3, "CHANNEL_PARTNER", true), module(4, "AUDIT", true), module(5, "ROLE", true));
        List<Permission> perms = List.of(
                perm(1, 1, "VIEW"), perm(2, 1, "CREATE"), perm(3, 1, "EDIT"), perm(4, 2, "VIEW"),
                perm(5, 3, "VIEW"), perm(6, 4, "VIEW"), perm(7, 4, "EXPORT"), perm(8, 5, "VIEW"), perm(9, 5, "EDIT"));
        when(moduleRepository.findByDeletedFalseOrderBySortOrderAsc()).thenReturn(modules);
        when(permissionRepository.findByDeletedFalseOrderByIdAsc()).thenReturn(perms);
        when(roleRepository.findByIdAndDeletedFalse(ROLE_ID)).thenReturn(Optional.of(role(ROLE_ID, "ACCOUNTANT", false)));
    }

    @AfterEach
    void tearDown() {
        tenant.close();
    }

    // ---------- (1) Super Admin only, enforced in the service ----------

    @Test
    void nonSuperAdminCannotCreateUpdateDeleteOrChangePermissions() {
        doThrow(new ForbiddenException("FORBIDDEN", "Only Super Admin can perform this action"))
                .when(guard).requireSuperAdmin(anyString());
        RoleRequestDto req = roleReq("TECHNICIAN", "Technician", null);

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.update(ROLE_ID, req)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(ROLE_ID)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(Map.of("QUOTATION", List.of("VIEW")))))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(rolePermissionRepository, auditService, permissionCache);
        verify(roleRepository, never()).save(any());
    }

    // ---------- (2) platform-only modules ----------

    @ParameterizedTest
    @ValueSource(strings = {"CHANNEL_PARTNER", "AUDIT", "ROLE", "audit", " Role "})
    void platformOnlyModulesCanNeverBeGranted(String moduleKey) {
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(Map.of(moduleKey, List.of("VIEW")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("platform-only");

        verify(rolePermissionRepository, never()).deleteByRoleId(any());
        verify(rolePermissionRepository, never()).saveAll(any());
        verify(permissionCache, never()).evict(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void platformOnlyModuleCannotBeSmuggledInAMixedRequest() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("QUOTATION", List.of("VIEW"));
        g.put("AUDIT", List.of("EXPORT"));
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(g)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("platform-only");
        verify(rolePermissionRepository, never()).saveAll(any());   // nothing half-saved
    }

    @Test
    void platformOnlyModuleWithNoActionsIsHarmless() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("QUOTATION", List.of("VIEW"));
        g.put("AUDIT", List.of());
        assertThatCode(() -> service.updatePermissions(ROLE_ID, grant(g))).doesNotThrowAnyException();
    }

    // ---------- request validation ----------

    @Test
    void rejectsUnknownModuleUnknownActionMissingViewAndBlankAction() {
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(Map.of("NOPE", List.of("VIEW")))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Unknown module");
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(Map.of("PRODUCT", List.of("VIEW", "DELETE")))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not available");
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(Map.of("QUOTATION", List.of("CREATE")))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("View is required");
        List<String> withBlank = new ArrayList<>();
        withBlank.add("VIEW"); withBlank.add(" ");
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(Map.of("QUOTATION", withBlank))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Blank action");
        assertThatThrownBy(() -> service.updatePermissions(ROLE_ID, grant(null)))
                .isInstanceOf(BusinessException.class);
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void superAdminRoleCannotBeEdited() {
        when(roleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(role(1L, "SUPER_ADMIN", true)));
        assertThatThrownBy(() -> service.updatePermissions(1L, grant(Map.of("QUOTATION", List.of("VIEW")))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(1L, roleReq("SUPER_ADMIN", "Renamed", null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void savesTheGrantedPermissions() {
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID))
                .thenReturn(List.of("QUOTATION:VIEW"), List.of("QUOTATION:VIEW", "QUOTATION:CREATE", "PRODUCT:VIEW"));

        var result = service.updatePermissions(ROLE_ID,
                grant(Map.of("QUOTATION", List.of("view", "CREATE"), "PRODUCT", List.of("VIEW"))));

        ArgumentCaptor<Iterable> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
        verify(rolePermissionRepository).saveAll(saved.capture());
        List<Long> permissionIds = new ArrayList<>();
        for (Object o : saved.getValue()) permissionIds.add(((RolePermission) o).getPermissionId());
        assertThat(permissionIds).containsExactlyInAnyOrder(1L, 2L, 4L);
        assertThat(result.getGranted()).containsKeys("QUOTATION", "PRODUCT");
    }

    // ---------- (3) cache invalidation ----------

    @Test
    void cacheIsEvictedOnlyAfterTheTransactionCommits() {
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID)).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updatePermissions(ROLE_ID, grant(Map.of("PRODUCT", List.of("VIEW"))));
            verify(permissionCache, never()).evict(ROLE_ID);              // not yet: could be reloaded stale
            for (TransactionSynchronization s : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
                s.afterCommit();
            }
            verify(permissionCache).evict(ROLE_ID);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cacheIsEvictedOnUpdateAndDelete() {
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
        when(roleRepository.countUsersByRoleId(ROLE_ID)).thenReturn(0L);

        service.update(ROLE_ID, roleReq("ACCOUNTANT", "Accountant II", null));
        verify(permissionCache).evict(ROLE_ID);

        service.delete(ROLE_ID);
        verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
        verify(permissionCache, org.mockito.Mockito.times(2)).evict(ROLE_ID);
    }

    // ---------- (6) audit ----------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void permissionChangeIsAuditedWithPreviousAndNewSets() {
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID))
                .thenReturn(List.of("QUOTATION:VIEW"), List.of("QUOTATION:VIEW", "QUOTATION:CREATE"));
        try (MockedStatic<DiffUtil> diff = mockStatic(DiffUtil.class)) {
            service.updatePermissions(ROLE_ID, grant(Map.of("QUOTATION", List.of("VIEW", "CREATE"))));

            ArgumentCaptor<Map> before = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Map> after = ArgumentCaptor.forClass(Map.class);
            diff.verify(() -> DiffUtil.diff(before.capture(), after.capture()));
            assertThat(before.getValue()).containsEntry("QUOTATION", "VIEW");
            assertThat(after.getValue()).containsEntry("QUOTATION", "VIEW,CREATE").containsEntry("PRODUCT", "");
        }
        verify(auditService).record(eq(AuditAction.PERMISSION_CHANGE), eq("ROLE"), eq("Role"),
                eq(ROLE_ID), eq("ACCOUNTANT"), any(), any());
    }

    @Test
    void unchangedPermissionsAreNeitherWrittenNorAudited() {
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID)).thenReturn(List.of("PRODUCT:VIEW"));
        service.updatePermissions(ROLE_ID, grant(Map.of("PRODUCT", List.of("VIEW"))));
        verify(rolePermissionRepository, never()).saveAll(any());
        verifyNoInteractions(auditService);
        verify(permissionCache, never()).evict(any());
    }

    // ---------- (4) reusable standard roles ----------

    @Test
    void superAdminCanCreateAReusableStandardRole() {
        when(roleRepository.existsByCodeAndDeletedFalse("ACCOUNTANT_2")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> {
            Role r = i.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 10L);
            return r;
        });

        service.create(roleReq(" accountant_2 ", " Accountant ", null));

        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("ACCOUNTANT_2");
        assertThat(saved.getValue().getName()).isEqualTo("Accountant");
        assertThat(saved.getValue().isSystem()).isFalse();
        assertThat(saved.getValue().getDataScope()).isEqualTo(DataScope.PARTNER);
        verify(auditService).record(eq(AuditAction.CREATE), eq("ROLE"), eq("Role"), eq(10L), eq("ACCOUNTANT_2"), any(), any());
    }

    @Test
    void createRejectsBadCodeDuplicateAllScopeAndUnknownScope() {
        assertThatThrownBy(() -> service.create(roleReq("1BAD", "x", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(roleReq("AB", "x", null))).isInstanceOf(BusinessException.class);
        when(roleRepository.existsByCodeAndDeletedFalse("TECHNICIAN")).thenReturn(true);
        assertThatThrownBy(() -> service.create(roleReq("TECHNICIAN", "x", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(roleReq("FIELD_LEAD", "x", "ALL")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> service.create(roleReq("FIELD_LEAD", "x", "GALAXY")))
                .isInstanceOf(BusinessException.class);            // used to leak as IllegalArgumentException (500)
        verify(roleRepository, never()).save(any());
    }

    @Test
    void systemRolesAndRolesInUseCannotBeDeleted() {
        when(roleRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(role(2L, "CHANNEL_PARTNER_ADMIN", true)));
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(BusinessException.class);

        when(roleRepository.countUsersByRoleId(ROLE_ID)).thenReturn(3L);
        assertThatThrownBy(() -> service.delete(ROLE_ID)).isInstanceOf(BusinessException.class);
        verify(rolePermissionRepository, never()).deleteByRoleId(any());
    }

    // ---------- fixtures ----------

    private static AppModule module(long id, String code, boolean platformOnly) {
        AppModule m = new AppModule();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCode(code); m.setName(code); m.setPlatformOnly(platformOnly); m.setSortOrder((int) id);
        return m;
    }

    private static Permission perm(long id, long moduleId, String action) {
        Permission p = new Permission();
        ReflectionTestUtils.setField(p, "id", id);
        p.setModuleId(moduleId); p.setAction(action);
        // module code is looked up from the catalog order used in setUp()
        String[] codes = {"", "QUOTATION", "PRODUCT", "CHANNEL_PARTNER", "AUDIT", "ROLE"};
        p.setCode(codes[(int) moduleId] + ":" + action);
        return p;
    }

    private static Role role(long id, String code, boolean system) {
        Role r = new Role();
        ReflectionTestUtils.setField(r, "id", id);
        r.setCode(code); r.setName(code); r.setSystem(system);
        r.setDataScope(system && code.equals("SUPER_ADMIN") ? DataScope.ALL : DataScope.PARTNER);
        r.setStatus(com.solar.Quotation.Enum.RecordStatus.ACTIVE);
        return r;
    }

    private static RoleRequestDto roleReq(String code, String name, String scope) {
        RoleRequestDto r = new RoleRequestDto();
        r.setCode(code); r.setName(name); r.setDataScope(scope);
        return r;
    }

    private static RolePermissionUpdateRequestDto grant(Map<String, List<String>> g) {
        RolePermissionUpdateRequestDto r = new RolePermissionUpdateRequestDto();
        r.setGranted(g);
        return r;
    }
}