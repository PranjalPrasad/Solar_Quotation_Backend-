package com.solar.Quotation.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Masks a snapshot BEFORE it is stored in audit_log (previous_data, new_data, changes_json).
 * Masking is by field NAME, applied at every nesting level, so a new secret field is covered as long as its name
 * follows the convention (…password, …token, …secret). Add exact names to the sets below when it does not.
 *
 *   REDACT   -> "[REDACTED]"            secrets: nothing of the value is kept
 *   LAST4    -> "******3210"            identifiers: phone, bank account, Aadhaar, PAN, consumer number
 *   EMAIL    -> "j***@gmail.com"
 *
 * Booleans and nulls are never masked (mustChangePassword=true is information, not a secret).
 */
public final class AuditMasker {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> REDACT = Set.of(
            "password", "passwordhash", "newpassword", "oldpassword", "currentpassword", "confirmpassword",
            "temporarypassword", "temppassword", "smtppassword", "mailpassword",
            "token", "refreshtoken", "accesstoken", "resettoken", "tokenhash",
            "secret", "clientsecret", "otp", "apikey", "authorization", "privatekey");

    private static final Set<String> LAST4 = Set.of(
            "phone", "mobile", "phonenumber", "mobilenumber", "contactnumber",
            "accountnumber", "bankaccount", "accountno", "aadhaar", "aadhar", "pan", "panno", "consumerno");

    private static final Set<String> EMAIL = Set.of("email", "emailid", "contactemail");

    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private AuditMasker() { }

    // ---------------------------------------------------------------- snapshots

    /** Deep copy with every sensitive value masked. Null in, null out. Never mutates the input. */
    public static Map<String, Object> mask(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : snapshot.entrySet()) {
            out.put(e.getKey(), maskValue(e.getKey(), e.getValue()));
        }
        return out;
    }

    /** Masks one value according to its field name. Nested maps and collections are masked recursively. */
    @SuppressWarnings("unchecked")
    public static Object maskValue(String field, Object value) {
        if (value == null || value instanceof Boolean) return value;
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                copy.put(k, maskValue(k, e.getValue()));
            }
            return copy;
        }
        if (value instanceof Collection<?> c) {
            List<Object> copy = new ArrayList<>(c.size());
            for (Object o : c) copy.add(maskValue(field, o));    // list items inherit the list's field name
            return copy;
        }
        String key = normalise(field);
        if (isRedacted(key)) return REDACTED;
        if (LAST4.contains(key)) return last4(String.valueOf(value));
        if (EMAIL.contains(key) || ("username".equals(key) && EMAIL_SHAPE.matcher(String.valueOf(value)).matches())) {
            return email(String.valueOf(value));
        }
        return value;
    }

    private static boolean isRedacted(String key) {
        return REDACT.contains(key) || key.endsWith("password") || key.endsWith("token") || key.endsWith("secret");
    }

    private static String normalise(String field) {
        return field == null ? "" : field.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    static String last4(String v) {
        String digits = v.replaceAll("\\s", "");
        if (digits.length() <= 4) return "****";
        return "*".repeat(Math.min(digits.length() - 4, 10)) + digits.substring(digits.length() - 4);
    }

    static String email(String v) {
        int at = v.indexOf('@');
        if (at < 1) return "***";
        return v.charAt(0) + "***" + v.substring(at);
    }

    // ---------------------------------------------------------------- changes_json

    /**
     * Field-level diff in the shape the screen already reads: [{field, old, new}].
     * The comparison runs on the RAW values (so a change hidden by a partial mask is still detected), and the
     * values written out are the MASKED ones. A redacted secret that changed shows as [REDACTED] -> [REDACTED].
     *
     *   before == null            : CREATE, every non-null field of after
     *   after  == null            : DELETE, every non-null field of before
     *   both present              : UPDATE, fields whose value differs
     */
    public static List<Map<String, String>> changes(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, String>> out = new ArrayList<>();
        Set<String> fields = new LinkedHashSet<>();
        if (after != null) fields.addAll(after.keySet());
        if (before != null) fields.addAll(before.keySet());
        for (String f : fields) {
            Object rawOld = before == null ? null : before.get(f);
            Object rawNew = after == null ? null : after.get(f);
            if (before == null || after == null) {
                if ((before == null ? rawNew : rawOld) == null) continue;       // skip empty fields on create / delete
            }
            if (java.util.Objects.equals(text(rawOld), text(rawNew))) continue;
            Map<String, String> row = new LinkedHashMap<>();
            row.put("field", f);
            String shownOld = text(maskValue(f, rawOld));
            String shownNew = text(maskValue(f, rawNew));
            if (shownOld != null && shownOld.equals(shownNew) && !REDACTED.equals(shownNew)) {
                shownNew = shownNew + " (changed)";        // only the hidden part of the value changed
            }
            row.put("old", shownOld);
            row.put("new", shownNew);
            out.add(row);
        }
        return out;
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}