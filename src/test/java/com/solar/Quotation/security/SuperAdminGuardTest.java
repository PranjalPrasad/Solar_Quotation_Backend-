package com.solar.Quotation.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SuperAdminGuardTest {

    RoleRepository roleRepository = mock(RoleRepository.class);
    SuperAdminGuard guard = new SuperAdminGuard(roleRepository);
    MockedStatic<TenantContext> tenant;
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(SuperAdminGuard.class);

    @BeforeEach
    void setUp() {
        tenant = mockStatic(TenantContext.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        tenant.close();
    }

    private void actingAs(long userId, long roleId, String roleCode) {
        CurrentUser u = mock(CurrentUser.class);
        when(u.getUserId()).thenReturn(userId);
        when(u.getRoleId()).thenReturn(roleId);
        tenant.when(TenantContext::get).thenReturn(u);
        Role r = new Role();
        r.setCode(roleCode);
        when(roleRepository.findByIdAndDeletedFalse(roleId)).thenReturn(Optional.of(r));
    }

    @Test
    void superAdminPasses() {
        actingAs(1, 1, "SUPER_ADMIN");
        assertThatCode(() -> guard.requireSuperAdmin("ROLE_CREATE")).doesNotThrowAnyException();
        assertThat(logs.list).isEmpty();
    }

    @Test
    void partnerAdminIsRefusedAndLoggedEvenWithRoleEditGranted() {
        actingAs(42, 2, "CHANNEL_PARTNER_ADMIN");
        assertThatThrownBy(() -> guard.requireSuperAdmin("ROLE_PERMISSIONS")).isInstanceOf(ForbiddenException.class);

        assertThat(logs.list).hasSize(1);
        ILoggingEvent e = logs.list.get(0);
        assertThat(e.getLevel()).isEqualTo(Level.WARN);
        assertThat(e.getFormattedMessage()).contains("[AUTHZ]", "userId=42", "SUPER_ADMIN_ONLY:ROLE_PERMISSIONS");
    }

    @Test
    void unauthenticatedIsRefused() {
        tenant.when(TenantContext::get).thenReturn(null);
        assertThatThrownBy(() -> guard.requireSuperAdmin("ROLE_DELETE")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletedRoleIsNotSuperAdmin() {
        CurrentUser u = mock(CurrentUser.class);
        when(u.getRoleId()).thenReturn(9L);
        tenant.when(TenantContext::get).thenReturn(u);
        when(roleRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.empty());
        assertThat(guard.isSuperAdmin()).isFalse();
    }
}