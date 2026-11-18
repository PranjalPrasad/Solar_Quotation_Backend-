package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private final JwtUtil jwtUtil = new JwtUtil("unit-test-secret-unit-test-secret-0123456789abcdef", 10);
    private AppUser user;
    private ChannelPartner partner;
    private Role role;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        ChannelPartnerRepository partners = mock(ChannelPartnerRepository.class);

        user = mock(AppUser.class);
        when(user.getId()).thenReturn(5L);
        when(user.getName()).thenReturn("Bob");
        when(user.getStatus()).thenReturn(RecordStatus.ACTIVE);
        when(user.getChannelPartnerId()).thenReturn(7L);
        when(user.getRoleId()).thenReturn(3L);
        when(users.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(user));

        partner = mock(ChannelPartner.class);
        when(partner.getStatus()).thenReturn(RecordStatus.ACTIVE);
        when(partners.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(partner));

        role = mock(Role.class);
        when(role.getId()).thenReturn(3L);
        when(role.getCode()).thenReturn("CHANNEL_PARTNER_ADMIN");
        when(role.getDataScope()).thenReturn(DataScope.PARTNER);
        when(role.getStatus()).thenReturn(RecordStatus.ACTIVE);
        when(roles.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(role));

        filter = new JwtAuthFilter(jwtUtil, users, roles, partners, JsonMapper.builder().build());
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.addHeader("Authorization", "Bearer " + jwtUtil.createAccessToken(5L, "CHANNEL_PARTNER_ADMIN", 7L));
        return r;
    }

    private MockFilterChain run(MockHttpServletRequest req, MockHttpServletResponse res) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return chain;
    }

    // ------------------------------------------------------------ forced password change

    @Test
    void forcedPasswordChangeBlocksEveryOtherEndpoint() throws Exception {
        when(user.isMustChangePassword()).thenReturn(true);
        MockHttpServletResponse res = new MockHttpServletResponse();

        MockFilterChain chain = run(request("GET", "/api/quotations"), res);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
        assertNull(chain.getRequest(), "controller must not be reached");
    }

    @Test
    void forcedPasswordChangeStillAllowsChangePasswordMeAndLogout() throws Exception {
        when(user.isMustChangePassword()).thenReturn(true);
        String[][] allowed = {{"POST", "/api/auth/change-password"}, {"GET", "/api/auth/me"}, {"POST", "/api/auth/logout"}};
        for (String[] c : allowed) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = run(request(c[0], c[1]), res);
            assertNotNull(chain.getRequest(), c[1] + " must pass");
            assertEquals(200, res.getStatus());
        }
    }

    @Test
    void normalUserPassesAfterPasswordWasChanged() throws Exception {
        when(user.isMustChangePassword()).thenReturn(false);
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertNotNull(run(request("GET", "/api/quotations"), res).getRequest());
        assertEquals(200, res.getStatus());
    }

    // ------------------------------------------------------------ deactivation is immediate

    @Test
    void deactivatedUserIsBlockedOnTheVeryNextRequest() throws Exception {
        when(user.getStatus()).thenReturn(RecordStatus.INACTIVE);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = run(request("GET", "/api/quotations"), res);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("ACCOUNT_INACTIVE"));
        assertNull(chain.getRequest());
    }

    @Test
    void deactivatedPartnerIsBlockedOnTheVeryNextRequest() throws Exception {
        when(partner.getStatus()).thenReturn(RecordStatus.INACTIVE);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = run(request("GET", "/api/quotations"), res);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("PARTNER_INACTIVE"));
        assertNull(chain.getRequest());
    }

    @Test
    void deactivatedRoleIsBlocked() throws Exception {
        when(role.getStatus()).thenReturn(RecordStatus.INACTIVE);
        MockHttpServletResponse res = new MockHttpServletResponse();
        run(request("GET", "/api/quotations"), res);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("ROLE_INACTIVE"));
    }

    // ------------------------------------------------------------ access tokens die with a password change / revocation

    @Test
    void accessTokenIssuedBeforeRevocationIsRejected() throws Exception {
        when(user.getTokenValidAfter()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(5));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = run(request("GET", "/api/quotations"), res);
        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("TOKEN_REVOKED"));
        assertNull(chain.getRequest());
    }

    @Test
    void accessTokenIssuedAfterRevocationIsAccepted() throws Exception {
        when(user.getTokenValidAfter()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertNotNull(run(request("GET", "/api/quotations"), res).getRequest());
    }
}