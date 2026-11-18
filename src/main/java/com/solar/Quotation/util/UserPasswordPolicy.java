package com.solar.Quotation.util;

import com.solar.Quotation.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One password policy for create, admin reset, forgot-password and change-password.
 * 10-100 characters, upper + lower case, a digit and a symbol, no spaces,
 * and it must not contain the username or the name part of the e-mail address.
 */
@Component
public class UserPasswordPolicy {

    public static final Pattern RULE = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{10,100}$");

    public static final String MESSAGE =
            "Password must be 10-100 characters with upper case, lower case, a digit and a symbol, and no spaces";

    private static final SecureRandom RNG = new SecureRandom();

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SYMBOL = "!@#$%&*?";

    /** Returns the password when it is acceptable, otherwise throws a VALIDATION_ERROR. */
    public String validate(String raw, String username, String email) {
        if (raw == null || !RULE.matcher(raw).matches()) {
            throw new BusinessException("VALIDATION_ERROR", MESSAGE);
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        String emailName = email == null ? "" : email.split("@")[0].toLowerCase(Locale.ROOT);
        boolean hasUsername = username != null && username.length() >= 3
                && lower.contains(username.toLowerCase(Locale.ROOT));
        boolean hasEmailName = emailName.length() >= 3 && lower.contains(emailName);
        if (hasUsername || hasEmailName) {
            throw new BusinessException("VALIDATION_ERROR", "Password must not contain the username or e-mail name");
        }
        return raw;
    }

    /** 14 characters, always satisfies RULE, avoids look-alike characters (0/O, 1/l/I). */
    public String generateTemporary() {
        List<Character> chars = new ArrayList<>();
        for (String set : new String[]{UPPER, UPPER, LOWER, LOWER, LOWER, LOWER, DIGIT, DIGIT, DIGIT, SYMBOL, SYMBOL}) {
            chars.add(pick(set));
        }
        String all = UPPER + LOWER + DIGIT + SYMBOL;
        while (chars.size() < 14) {
            chars.add(pick(all));
        }
        Collections.shuffle(chars, RNG);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static char pick(String set) {
        return set.charAt(RNG.nextInt(set.length()));
    }
}