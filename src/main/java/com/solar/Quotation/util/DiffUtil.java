package com.solar.Quotation.util;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds the changes_json list for audit rows. Sensitive fields are never included. */
public final class DiffUtil {
    private DiffUtil() { }

    private static final Set<String> SENSITIVE = Set.of("password", "passwordHash", "token", "refreshToken");

    public static List<Map<String, String>> diff(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            String field = e.getKey();
            if (SENSITIVE.contains(field)) continue;
            Object oldV = before == null ? null : before.get(field);
            if (!Objects.equals(oldV, e.getValue())) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("field", field);
                row.put("old", oldV == null ? null : String.valueOf(oldV));
                row.put("new", e.getValue() == null ? null : String.valueOf(e.getValue()));
                out.add(row);
            }
        }
        return out;
    }
}
