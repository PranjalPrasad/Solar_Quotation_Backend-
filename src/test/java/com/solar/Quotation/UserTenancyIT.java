package com.solar.Quotation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-partner access by id (must be 404) and role assignment limits.
 *
 * ADAPT BEFORE RUNNING (the parts I could not see in your code):
 *  - seed() uses setters/constants I guessed on ChannelPartner and Role (setCode, setName, setPartnerType,
 *    setDataScope, setStatus, PartnerType.CHANNEL_PARTNER). Change them to match your entities.
 *  - The roles used by the real permission system (USER:VIEW, USER:CREATE, USER:EDIT, USER:RESET_PASSWORD)
 *    must be granted to the seeded admin roles; seed those permission rows here the way your RBAC stores them.
 *  - login() posts {"username","password"} to /api/auth/login and reads data.accessToken. Change if different.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserTenancyIT {

    private static final String PASSWORD = "Test!Passw0rd#1";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ChannelPartnerRepository partners;
    @Autowired JdbcTemplate jdbc;

    ChannelPartner platform, partnerA, partnerB;
    Role superAdminRole, partnerAdminRole, salesRole, accountantRole;
    AppUser superAdmin, adminA, userA, adminB, userB;

    @BeforeEach
    void seed() {
        platform = partner("PLATFORM", PartnerType.PLATFORM);
        partnerA = partner("PARTNER A", PartnerType.CHANNEL_PARTNER);
        partnerB = partner("PARTNER B", PartnerType.CHANNEL_PARTNER);

        superAdminRole = role("SUPER_ADMIN", DataScope.ALL);
        partnerAdminRole = role("CHANNEL_PARTNER_ADMIN", DataScope.PARTNER);
        salesRole = role("SALES", DataScope.PARTNER);
        accountantRole = role("ACCOUNTANT", DataScope.PARTNER);

        // PLATFORM and A may hand out SALES; only B may hand out ACCOUNTANT. SUPER_ADMIN is in nobody's list.
        allow(platform, salesRole);
        allow(partnerA, salesRole);
        allow(partnerB, accountantRole);

        superAdmin = user(platform, superAdminRole, "superadmin");
        adminA = user(partnerA, partnerAdminRole, "admin.a");
        userA = user(partnerA, salesRole, "user.a");
        adminB = user(partnerB, partnerAdminRole, "admin.b");
        userB = user(partnerB, accountantRole, "user.b");
    }

    // ------------------------------------------------ cross-partner access by id -> 404

    @Test
    void adminA_cannotGetUserOfB() throws Exception {
        mvc.perform(get("/api/users/{id}", userB.getId()).header("Authorization", bearer(adminA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminA_cannotUpdateUserOfB() throws Exception {
        mvc.perform(put("/api/users/{id}", userB.getId()).header("Authorization", bearer(adminA))
                        .contentType(JSON).content(updateBody(salesRole.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminA_cannotDeactivateUserOfB() throws Exception {
        mvc.perform(patch("/api/users/{id}/status", userB.getId()).header("Authorization", bearer(adminA))
                        .contentType(JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminA_cannotResetPasswordOfB() throws Exception {
        mvc.perform(post("/api/users/{id}/reset-password", userB.getId()).header("Authorization", bearer(adminA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminA_listIgnoresPartnerIdParameter() throws Exception {
        mvc.perform(get("/api/users").param("partnerId", partnerB.getId().toString())
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.partner.id == " + partnerB.getId() + ")]").isEmpty());
    }

    @Test
    void adminA_createIgnoresPartnerIdInBody() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", bearer(adminA)).contentType(JSON)
                        .content(createBody(salesRole.getId(), partnerB.getId(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partner.id").value(partnerA.getId()));
    }

    // ------------------------------------------------ role assignment limits

    @Test
    void adminA_cannotAssignRoleOutsideAllowList() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", bearer(adminA)).contentType(JSON)
                        .content(createBody(accountantRole.getId(), null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void nobodyCanAssignAllScopeRole_evenSuperAdmin() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", bearer(superAdmin)).contentType(JSON)
                        .content(createBody(superAdminRole.getId(), platform.getId(), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdmin_createsPlatformStaffWithNonAllRole() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", bearer(superAdmin)).contentType(JSON)
                        .content(createBody(salesRole.getId(), platform.getId(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partner.id").value(platform.getId()))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.username").isNotEmpty());
    }

    @Test
    void superAdmin_cannotUseRoleNotAllowedForPlatform() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", bearer(superAdmin)).contentType(JSON)
                        .content(createBody(accountantRole.getId(), platform.getId(), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminA_cannotChangeOwnRole() throws Exception {
        mvc.perform(put("/api/users/{id}", adminA.getId()).header("Authorization", bearer(adminA))
                        .contentType(JSON).content(updateBody(salesRole.getId())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adminA_cannotDeactivateSelf() throws Exception {
        mvc.perform(patch("/api/users/{id}/status", adminA.getId()).header("Authorization", bearer(adminA))
                        .contentType(JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void roleOptions_onlyAllowedRolesForCaller() throws Exception {
        mvc.perform(get("/api/users/role-options").header("Authorization", bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", hasItem("SALES")))
                .andExpect(jsonPath("$.data[*].code", not(hasItem("ACCOUNTANT"))))
                .andExpect(jsonPath("$.data[*].code", not(hasItem("SUPER_ADMIN"))));
    }

    @Test
    void roleOptions_clientPartnerIdIgnoredForPartnerAdmin() throws Exception {
        mvc.perform(get("/api/users/role-options").param("partnerId", partnerB.getId().toString())
                        .header("Authorization", bearer(adminA)))
                .andExpect(jsonPath("$.data[*].code", not(hasItem("ACCOUNTANT"))));
    }

    // ------------------------------------------------ password policy / one-time password

    @Test
    void weakPasswordRejected() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", bearer(adminA)).contentType(JSON)
                        .content(createBody(salesRole.getId(), null, "abc12345")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void temporaryPasswordNeverReturnedOnGet() throws Exception {
        mvc.perform(get("/api/users/{id}", userA.getId()).header("Authorization", bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").doesNotExist());
    }

    @Test
    void resetPasswordGeneratesOneTimePasswordAndForcesChange() throws Exception {
        mvc.perform(post("/api/users/{id}/reset-password", userA.getId()).header("Authorization", bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    // ------------------------------------------------ helpers

    private ChannelPartner partner(String name, PartnerType type) {
        ChannelPartner p = new ChannelPartner();
        p.setCode(name.replace(' ', '_') + "_" + SEQ.incrementAndGet());
        p.setName(name);
        p.setPartnerType(type);
        return partners.save(p);
    }

    private Role role(String code, DataScope scope) {
        Role r = new Role();
        r.setCode(code);
        r.setName(code);
        r.setDataScope(scope);
        r.setStatus(RecordStatus.ACTIVE);
        return roles.save(r);
    }

    private void allow(ChannelPartner p, Role r) {
        // column names: change if your partner_allowed_role table differs (DESCRIBE partner_allowed_role)
        jdbc.update("insert into partner_allowed_role (partner_id, role_id) values (?, ?)", p.getId(), r.getId());
    }

    private AppUser user(ChannelPartner p, Role r, String username) {
        AppUser u = new AppUser();
        u.setChannelPartnerId(p.getId());
        u.setCode("USR" + SEQ.incrementAndGet());
        u.setName(username);
        u.setEmail(username + "@test.local");
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRoleId(r.getId());
        u.setStatus(RecordStatus.ACTIVE);
        u.setMustChangePassword(false);
        return users.save(u);
    }

    private String bearer(AppUser u) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", u.getUsername(), "password", PASSWORD));
        String json = mvc.perform(post("/api/auth/login").contentType(JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = mapper.readTree(json).path("data");
        return "Bearer " + data.path("accessToken").asText();
    }

    private String createBody(Long roleId, Long partnerId, String password) throws Exception {
        int n = SEQ.incrementAndGet();
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", "New User " + n);
        m.put("email", "new" + n + "@test.local");
        m.put("phone", "9876543210");
        m.put("roleId", roleId);
        if (partnerId != null) m.put("partnerId", partnerId);
        if (password != null) m.put("password", password);
        return mapper.writeValueAsString(m);
    }

    private String updateBody(Long roleId) throws Exception {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", "Changed Name");
        m.put("email", "changed" + SEQ.incrementAndGet() + "@test.local");
        m.put("roleId", roleId);
        return mapper.writeValueAsString(m);
    }
}