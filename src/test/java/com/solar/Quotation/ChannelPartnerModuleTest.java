package com.solar.Quotation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** superAdminToken()/partnerAdminToken() apne auth setup ke hisaab se implement karo (login endpoint ya JwtService). */
@SpringBootTest
@AutoConfigureMockMvc
class ChannelPartnerModuleTest {

    @Autowired MockMvc mvc;

    private String superAdminToken()   { throw new UnsupportedOperationException("wire to your auth"); }
    private String partnerAdminToken() { throw new UnsupportedOperationException("wire to your auth"); }

    @Test
    void partnerAdminGets403() throws Exception {
        for (String url : List.of("/api/channel-partners", "/api/partners", "/api/partners/options",
                "/api/channel-partners/2/allowed-roles")) {
            mvc.perform(get(url).header("Authorization", "Bearer " + partnerAdminToken()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/channel-partners/2/allowed-roles").contentType(APPLICATION_JSON)
                        .content("{\"roleIds\":[]}")
                        .header("Authorization", "Bearer " + partnerAdminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPartnerCreatesAdminUserWithTemporaryPassword() throws Exception {
        String body = """
                {"name":"Sun Co","email":"sun@x.com","adminUser":{"name":"Ravi","email":"ravi@x.com"}}""";
        mvc.perform(post("/api/channel-partners").contentType(APPLICATION_JSON)
                        .content(body).header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adminUser.username").isNotEmpty())
                .andExpect(jsonPath("$.data.adminUser.temporaryPassword").isNotEmpty());
        // + AppUserRepository se assert karo: user.isMustChangePassword() == true
    }

    @Test
    void deactivatedPartnerUserIsBlockedImmediately() throws Exception {
        // 1) partner admin token le, 2) super admin PATCH /api/channel-partners/{id}/status {"status":"INACTIVE"},
        // 3) partner admin ka koi bhi call => 403
    }
}