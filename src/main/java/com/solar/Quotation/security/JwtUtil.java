package com.solar.Quotation.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** Token carries only userId, roleCode, partnerId. Permissions are NOT stored here. */
@Component
public class JwtUtil {

    /** HS256 needs at least 256 bits = 32 bytes. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessMinutes;

    public JwtUtil(@Value("${sqms.jwt.secret:}") String secret,
                   @Value("${sqms.jwt.access-token-minutes}") long accessMinutes) {
        // An EMPTY JWT_SECRET (env var or .env line "JWT_SECRET=") overrides the default in
        // application.properties, so fail here with a clear message instead of a WeakKeyException.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is empty. JWT_SECRET is defined but has no value (check the .env file in the project root, "
                            + "the IntelliJ Run configuration environment variables and Windows environment variables). "
                            + "Remove the empty JWT_SECRET line, or set it to at least 32 random characters.");
        }
        byte[] bytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret is too short (" + bytes.length
                    + " bytes). Use at least " + MIN_SECRET_BYTES + " characters; 48 or more is recommended.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessMinutes = accessMinutes;
    }

    public String createAccessToken(Long userId, String roleCode, Long partnerId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roleCode", roleCode)
                .claim("partnerId", partnerId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessMinutes * 60_000L))
                .signWith(key)
                .compact();
    }

    /** Throws JwtException when invalid or expired. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getAccessSeconds() { return accessMinutes * 60L; }
}