package com.solar.Quotation.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyAndStartupTest {

    private static final String STRONG = "q8Zr2nV7xLw4Tb9Kc1Hd6Jm3Pf5Sg0UaYe8Ni2Ro4Vt7Xk1Wl3";
    private static final String DEFAULT = "solar-crm-dev-only-secret-key-please-change-me-before-prod-0123456789";

    @Test
    void bootstrapPasswordRules() {
        assertNotNull(PasswordPolicy.weakness("Short1a", PasswordPolicy.MIN_BOOTSTRAP));                 // < 12
        assertNotNull(PasswordPolicy.weakness("onlyletterslongenough", PasswordPolicy.MIN_BOOTSTRAP));   // no digit
        assertNotNull(PasswordPolicy.weakness("Password12345", PasswordPolicy.MIN_BOOTSTRAP));           // common
        assertNotNull(PasswordPolicy.weakness("aaaaaaaaaaaa1", PasswordPolicy.MIN_BOOTSTRAP));           // repetitive
        assertNotNull(PasswordPolicy.weakness("admin-Pranjal-2026", PasswordPolicy.MIN_BOOTSTRAP, "pranjal")); // personal
        assertNull(PasswordPolicy.weakness("Tr0ub4dor&3-horse-staple", PasswordPolicy.MIN_BOOTSTRAP, "admin"));
    }

    @Test
    void logMaskHidesTheLogin() {
        assertEquals("b***@example.com", LogMask.login("bob@example.com"));
        assertEquals("p***l", LogMask.login("pranjal"));
        assertFalse(LogMask.login("a\nb\r@x.com").contains("\n"));
    }

    private SecurityStartupValidator validator(String profile, String secret, String cors, boolean devLink, String dbPw) {
        MockEnvironment env = new MockEnvironment();
        if (profile != null) env.setActiveProfiles(profile);
        return new SecurityStartupValidator(env, secret, cors, devLink, dbPw);
    }

    @Test
    void prodRefusesTheDefaultAndWeakSecrets() {
        assertThrows(IllegalStateException.class, () -> validator("prod", DEFAULT, "https://app.example.com", false, "x9!Qv").validate());
        assertThrows(IllegalStateException.class, () -> validator("prod", "", "https://app.example.com", false, "x9!Qv").validate());
        assertThrows(IllegalStateException.class, () -> validator("prod", "short-secret", "https://app.example.com", false, "x9!Qv").validate());
        assertThrows(IllegalStateException.class, () -> validator("prod", "a".repeat(64), "https://app.example.com", false, "x9!Qv").validate());
    }

    @Test
    void prodRefusesWildcardCorsAndDevLinkLogging() {
        assertThrows(IllegalStateException.class, () -> validator("prod", STRONG, "*", false, "x9!Qv").validate());
        assertThrows(IllegalStateException.class, () -> validator("prod", STRONG, "https://app.example.com", true, "x9!Qv").validate());
    }

    @Test
    void prodStartsWithAStrongConfiguration() {
        assertDoesNotThrow(() -> validator("prod", STRONG, "https://app.example.com", false, "x9!Qv-long-db-pass").validate());
    }

    @Test
    void devOnlyWarns() {
        assertDoesNotThrow(() -> validator(null, "short", "*", true, "root").validate());
    }
}