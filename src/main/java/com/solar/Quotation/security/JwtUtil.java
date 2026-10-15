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

    private final SecretKey key;
    private final long accessMinutes;

    public JwtUtil(@Value("${sqms.jwt.secret}") String secret,
                   @Value("${sqms.jwt.access-token-minutes}") long accessMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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