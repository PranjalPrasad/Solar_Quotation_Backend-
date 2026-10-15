package com.solar.Quotation.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Refresh token helpers. Only the SHA-256 hash of a refresh token is ever stored. */
@Component
public class TokenUtil {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final SecureRandom random = new SecureRandom();
    private final long refreshDays;
    private final long refreshDaysRememberMe;

    public TokenUtil(@Value("${sqms.jwt.refresh-token-days:7}") long refreshDays,
                     @Value("${sqms.jwt.refresh-token-days-remember-me:30}") long refreshDaysRememberMe) {
        this.refreshDays = refreshDays;
        this.refreshDaysRememberMe = refreshDaysRememberMe;
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public long refreshDays(boolean rememberMe) {
        return rememberMe ? refreshDaysRememberMe : refreshDays;
    }

    /** 12 characters, always contains a letter and a digit, no look-alike characters. */
    public String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        sb.append("Q").append(random.nextInt(8) + 2);
        return sb.toString();
    }
}
