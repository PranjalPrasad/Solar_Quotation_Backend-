package com.solar.Quotation.security;

/** Masks identifiers for [AUTH] log lines and strips control characters (log forging). */
public final class LogMask {

    private LogMask() { }

    /** bob@example.com -> b***@example.com ; pranjal -> p***l */
    public static String login(String s) {
        if (s == null || s.isBlank()) return "-";
        String t = clean(s.trim());
        int at = t.indexOf('@');
        if (at > 0) return t.charAt(0) + "***" + t.substring(at);
        return t.length() <= 2 ? "***" : t.charAt(0) + "***" + t.charAt(t.length() - 1);
    }

    public static String ip(String s) {
        return s == null || s.isBlank() ? "unknown" : clean(s.trim());
    }

    private static String clean(String s) {
        String t = s.replaceAll("[\\r\\n\\t]", " ");
        return t.length() > 60 ? t.substring(0, 60) : t;
    }
}
