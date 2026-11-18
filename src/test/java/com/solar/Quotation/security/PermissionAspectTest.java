package com.solar.Quotation.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.exception.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PermissionAspectTest {

    PermissionCache cache = mock(PermissionCache.class);
    PermissionAspect aspect = new PermissionAspect(cache);
    MockedStatic<TenantContext> tenant;
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(PermissionAspect.class);

    @RequirePermission("ROLE:EDIT")
    void guardedMethod() { }

    private RequirePermission annotation() throws Exception {
        return getClass().getDeclaredMethod("guardedMethod").getAnnotation(RequirePermission.class);
    }

    @BeforeEach
    void setUp() {
        tenant = mockStatic(TenantContext.class);
        logs.start();
        logger.addAppender(logs);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("PUT", "/api/roles/5/permissions")));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        logger.detachAppender(logs);
        tenant.close();
    }

    private void actingAs(long userId, long roleId) {
        CurrentUser u = mock(CurrentUser.class);
        when(u.getUserId()).thenReturn(userId);
        when(u.getRoleId()).thenReturn(roleId);
        tenant.when(TenantContext::get).thenReturn(u);
    }

    @Test
    void deniedCheckLogsWarnWithUserPermissionAndPath() throws Exception {
        actingAs(42, 7);
        when(cache.permissionsForRole(7L)).thenReturn(Set.of("ROLE:VIEW"));

        assertThatThrownBy(() -> aspect.check(annotation())).isInstanceOf(ForbiddenException.class);

        assertThat(logs.list).hasSize(1);
        ILoggingEvent e = logs.list.get(0);
        assertThat(e.getLevel()).isEqualTo(Level.WARN);
        assertThat(e.getFormattedMessage())
                .contains("[AUTHZ]", "userId=42", "permission=ROLE:EDIT", "path=PUT /api/roles/5/permissions");
    }

    @Test
    void unauthenticatedDenialIsLoggedToo() throws Exception {
        tenant.when(TenantContext::get).thenReturn(null);
        assertThatThrownBy(() -> aspect.check(annotation())).isInstanceOf(ForbiddenException.class);
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void allowedCheckLogsNothing() throws Exception {
        actingAs(1, 1);
        when(cache.permissionsForRole(1L)).thenReturn(Set.of("ROLE:EDIT"));
        assertThatCode(() -> aspect.check(annotation())).doesNotThrowAnyException();
        assertThat(logs.list).isEmpty();
    }
}