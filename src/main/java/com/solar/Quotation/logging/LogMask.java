package com.solar.Quotation.logging;

/** Masks personal data before it goes into a log line. Use it for every email / phone you must mention. */
public final class LogMask {
    private LogMask() {}

    /** john.doe@gmail.com -> j***@gmail.com */
    public static String email(String email) {
        if (email == null || email.isBlank()) return "-";
        int at = email.indexOf('@');
        if (at < 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** +91 98765 43210 -> ******3210 */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) return "-";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 5) return "***";
        return "******" + digits.substring(digits.length() - 4);
    }
}
