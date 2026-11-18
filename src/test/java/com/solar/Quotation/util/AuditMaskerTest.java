package com.solar.Quotation.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditMaskerTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void secretsAreRedactedWhateverTheCase() {
        Map<String, Object> out = AuditMasker.mask(map("password", "Hunter2!", "passwordHash", "$2a$10$abc", "refreshToken", "t1",
                "NEW_PASSWORD", "x", "apiKey", "k", "smtpPassword", "p", "name", "Asha"));
        assertEquals("[REDACTED]", out.get("password"));
        assertEquals("[REDACTED]", out.get("passwordHash"));
        assertEquals("[REDACTED]", out.get("refreshToken"));
        assertEquals("[REDACTED]", out.get("NEW_PASSWORD"));
        assertEquals("[REDACTED]", out.get("apiKey"));
        assertEquals("[REDACTED]", out.get("smtpPassword"));
        assertEquals("Asha", out.get("name"));
    }

    @Test
    void anyFieldNamedLikeASecretIsRedactedByConvention() {
        Map<String, Object> out = AuditMasker.mask(map("resetLinkToken", "abc", "webhookSecret", "s", "temporaryPassword", "tmp"));
        assertEquals("[REDACTED]", out.get("resetLinkToken"));
        assertEquals("[REDACTED]", out.get("webhookSecret"));
        assertEquals("[REDACTED]", out.get("temporaryPassword"));
    }

    @Test
    void booleansAndNullsAreNeverMasked() {
        Map<String, Object> out = AuditMasker.mask(map("mustChangePassword", true, "passwordGenerated", false, "password", null));
        assertEquals(true, out.get("mustChangePassword"));
        assertEquals(false, out.get("passwordGenerated"));
        assertNull(out.get("password"));
    }

    @Test
    void identifiersKeepOnlyTheLastFourCharacters() {
        Map<String, Object> out = AuditMasker.mask(map("phone", "+91 98765 43210", "accountNumber", "50100234567890",
                "pan", "ABCDE1234F", "mobile", "123"));
        assertEquals("*********3210".length(), String.valueOf(out.get("phone")).length());
        assertTrue(String.valueOf(out.get("phone")).endsWith("3210"));
        assertFalse(String.valueOf(out.get("phone")).contains("98765"));
        assertTrue(String.valueOf(out.get("accountNumber")).endsWith("7890"));
        assertFalse(String.valueOf(out.get("accountNumber")).contains("5010023"));
        assertTrue(String.valueOf(out.get("pan")).endsWith("234F"));
        assertEquals("****", out.get("mobile"));
    }

    @Test
    void emailsKeepFirstLetterAndDomain() {
        Map<String, Object> out = AuditMasker.mask(map("email", "john.doe@gmail.com", "username", "ravi@sun.in", "code", "CUST-1"));
        assertEquals("j***@gmail.com", out.get("email"));
        assertEquals("r***@sun.in", out.get("username"));
        assertEquals("CUST-1", out.get("code"));
    }

    @Test
    void nestedMapsAndListsAreMaskedAndTheInputIsNotMutated() {
        Map<String, Object> item = map("sku", "P-1", "phone", "9999988888");
        Map<String, Object> input = map("items", List.of(item), "owner", map("password", "pw"));
        Map<String, Object> out = AuditMasker.mask(input);
        @SuppressWarnings("unchecked")
        Map<String, Object> maskedItem = (Map<String, Object>) ((List<?>) out.get("items")).get(0);
        assertTrue(String.valueOf(maskedItem.get("phone")).endsWith("8888"));
        assertFalse(String.valueOf(maskedItem.get("phone")).contains("99999"));
        @SuppressWarnings("unchecked")
        Map<String, Object> owner = (Map<String, Object>) out.get("owner");
        assertEquals("[REDACTED]", owner.get("password"));
        assertEquals("9999988888", item.get("phone"));                  // original untouched
        assertEquals("pw", ((Map<?, ?>) input.get("owner")).get("password"));
    }

    @Test
    void nullSnapshotStaysNull() {
        assertNull(AuditMasker.mask(null));
    }

    // ------------------------------------------------------------------ changes_json

    @Test
    void createListsEveryNonNullFieldMasked() {
        List<Map<String, String>> c = AuditMasker.changes(null, map("name", "Asha", "phone", "9876543210", "remarks", null));
        assertEquals(2, c.size());
        assertEquals("name", c.get(0).get("field"));
        assertNull(c.get(0).get("old"));
        assertEquals("Asha", c.get(0).get("new"));
        assertTrue(c.get(1).get("new").endsWith("3210"));
    }

    @Test
    void updateListsOnlyWhatChanged() {
        List<Map<String, String>> c = AuditMasker.changes(map("name", "A", "city", "Pune"), map("name", "B", "city", "Pune"));
        assertEquals(1, c.size());
        assertEquals("name", c.get(0).get("field"));
        assertEquals("A", c.get(0).get("old"));
        assertEquals("B", c.get(0).get("new"));
    }

    @Test
    void deleteKeepsThePreviousValuesWithNewNull() {
        List<Map<String, String>> c = AuditMasker.changes(map("name", "A", "email", "a@x.com"), null);
        assertEquals(2, c.size());
        assertEquals("A", c.get(0).get("old"));
        assertNull(c.get(0).get("new"));
        assertEquals("a***@x.com".replace("a***", "a***"), c.get(1).get("old"));
    }

    @Test
    void aChangeHiddenByTheMaskIsStillReported() {
        List<Map<String, String>> c = AuditMasker.changes(map("accountNumber", "5010000000007890"), map("accountNumber", "5010111111117890"));
        assertEquals(1, c.size());
        assertTrue(c.get(0).get("new").endsWith("(changed)"), c.get(0).get("new"));
    }

    @Test
    void aChangedSecretIsReportedButNeverShown() {
        List<Map<String, String>> c = AuditMasker.changes(map("passwordHash", "old-hash"), map("passwordHash", "new-hash"));
        assertEquals(1, c.size());
        assertEquals("[REDACTED]", c.get(0).get("old"));
        assertEquals("[REDACTED]", c.get(0).get("new"));
    }

    @Test
    void anUnchangedSecretIsNotListed() {
        assertTrue(AuditMasker.changes(map("passwordHash", "same"), map("passwordHash", "same")).isEmpty());
    }
}