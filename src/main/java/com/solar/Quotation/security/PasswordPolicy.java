package com.solar.Quotation.security;

import com.solar.Quotation.exception.BusinessException;

import java.util.Locale;
import java.util.Set;

/**
 * Static password rules used by bootstrap admin, change-password and forgot-password.
 * (Admin-created users use util.UserPasswordPolicy, which also generates temporary passwords.)
 */
public final class PasswordPolicy {

    public static final int MIN_USER = 10;
    public static final int MIN_BOOTSTRAP = 12;
    public static final int MAX = 100;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "password12345",
            "passw0rd", "qwerty", "qwerty123", "qwertyuiop", "letmein", "welcome", "welcome1",
            "admin", "admin123", "administrator", "iloveyou", "changeme", "123456", "12345678",
            "123456789", "1234567890", "abc123", "monkey", "dragon", "solar123");

    private PasswordPolicy() {
    }

    /** Lower-case part of an e-mail before '@', or null. */
    public static String localPart(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        return (at > 0 ? email.substring(0, at) : email).trim().toLowerCase(Locale.ROOT);
    }

    /** Returns a reason when the password is weak, or null when it is acceptable. */
    public static String weakness(String raw, int minLength, String... personal) {
        if (raw == null || raw.length() < minLength) {
            return "Password must be at least " + minLength + " characters";
        }
        if (raw.length() > MAX) return "Password must be at most " + MAX + " characters";
        if (raw.chars().anyMatch(Character::isWhitespace)) return "Password must not contain spaces";
        if (raw.chars().noneMatch(Character::isUpperCase)) return "Password needs an upper case letter";
        if (raw.chars().noneMatch(Character::isLowerCase)) return "Password needs a lower case letter";
        if (raw.chars().noneMatch(Character::isDigit)) return "Password needs a digit";
        if (raw.chars().allMatch(Character::isLetterOrDigit)) return "Password needs a symbol";

        String lower = raw.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) return "Password is too common";
        if (raw.chars().distinct().count() <= 3) return "Password is too repetitive";
        String stripped = lower.replaceAll("[^a-z0-9]", "");
        if (COMMON.contains(stripped)) return "Password is too common";
        if (raw.chars().distinct().count() < raw.length() / 3) return "Password is too repetitive";

        if (personal != null) {
            for (String p : personal) {
                if (p != null && p.trim().length() >= 3 && lower.contains(p.trim().toLowerCase(Locale.ROOT))) {
                    return "Password must not contain your username or e-mail name";
                }
            }
        }
        return null;
    }

    /** Throws a VALIDATION_ERROR when the password is weak. */
    public static void require(String raw, int minLength, String... personal) {
        String weak = weakness(raw, minLength, personal);
        if (weak != null) throw new BusinessException("VALIDATION_ERROR", weak);
    }
}