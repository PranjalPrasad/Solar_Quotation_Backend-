package com.solar.Quotation.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start with the "prod" profile when the JWT secret is weak or the well-known default,
 * or when obviously unsafe settings are left on. Outside prod it only logs warnings. The secret is never logged.
 */
@Component
public class SecurityStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);
    static final int MIN_PROD_SECRET_LENGTH = 48;
    private static final Set<String> KNOWN_DEFAULTS =
            Set.of("solar-crm-dev-only-secret-key-please-change-me-before-prod-0123456789");
    private static final String[] BAD_FRAGMENTS = {"change-me", "changeme", "dev-only", "secret", "password", "example"};

    private final Environment env;
    private final String jwtSecret;
    private final String corsOrigins;
    private final boolean devLogLink;
    private final String dbPassword;

    public SecurityStartupValidator(Environment env,
                                    @Value("${sqms.jwt.secret:}") String jwtSecret,
                                    @Value("${app.cors.allowed-origins:*}") String corsOrigins,
                                    @Value("${app.mail.dev-log-link:false}") boolean devLogLink,
                                    @Value("${spring.datasource.password:}") String dbPassword) {
        this.env = env;
        this.jwtSecret = jwtSecret;
        this.corsOrigins = corsOrigins;
        this.devLogLink = devLogLink;
        this.dbPassword = dbPassword;
    }

    @PostConstruct
    void validate() {
        boolean prod = env.acceptsProfiles(Profiles.of("prod", "production"));
        List<String> problems = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.isBlank()) {
            problems.add("JWT_SECRET is not set");
        } else {
            String lower = jwtSecret.toLowerCase(Locale.ROOT);
            if (KNOWN_DEFAULTS.contains(jwtSecret)) problems.add("JWT_SECRET is the built-in development default");
            if (jwtSecret.length() < MIN_PROD_SECRET_LENGTH) problems.add("JWT_SECRET is shorter than " + MIN_PROD_SECRET_LENGTH + " characters");
            if (jwtSecret.chars().distinct().count() < 16) problems.add("JWT_SECRET has too little variety (generate one with: openssl rand -base64 48)");
            for (String f : BAD_FRAGMENTS) {
                if (lower.contains(f)) { problems.add("JWT_SECRET looks like a placeholder"); break; }
            }
        }
        if (corsOrigins != null && corsOrigins.contains("*")) problems.add("app.cors.allowed-origins is '*'");
        if (devLogLink) problems.add("app.mail.dev-log-link is true (reset links would be written to the log)");
        if (dbPassword == null || dbPassword.isBlank() || "root".equals(dbPassword)) problems.add("database password is empty or 'root'");

        if (problems.isEmpty()) return;
        if (prod) {
            throw new IllegalStateException("[SECURITY] Refusing to start in prod: " + String.join("; ", problems));
        }
        log.warn("[SECURITY] Development configuration is not production-safe: {}", String.join("; ", problems));
    }
}