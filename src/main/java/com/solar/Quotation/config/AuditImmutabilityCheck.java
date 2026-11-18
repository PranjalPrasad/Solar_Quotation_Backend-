package com.solar.Quotation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * At start-up: are the append-only triggers of audit_log installed (V101__audit_log_immutable.sql)?
 *   - missing, profile "prod"  -> the application refuses to start
 *   - missing, other profiles  -> one ERROR line "[AUDIT] ..." and the application starts (local development)
 */
@Component
public class AuditImmutabilityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditImmutabilityCheck.class);
    static final List<String> REQUIRED = List.of("trg_audit_log_no_update", "trg_audit_log_no_delete");

    private final JdbcTemplate jdbc;
    private final Environment env;

    public AuditImmutabilityCheck(JdbcTemplate jdbc, Environment env) {
        this.jdbc = jdbc;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> found;
        try {
            found = jdbc.queryForList(
                    "SELECT TRIGGER_NAME FROM information_schema.TRIGGERS "
                            + "WHERE TRIGGER_SCHEMA = DATABASE() AND EVENT_OBJECT_TABLE = 'audit_log'", String.class);
        } catch (RuntimeException ex) {
            log.error("[AUDIT] could not check the audit_log triggers error={}", ex.getClass().getSimpleName());
            return;
        }
        List<String> missing = REQUIRED.stream().filter(t -> !found.contains(t)).toList();
        if (missing.isEmpty()) {
            log.info("[AUDIT] audit_log append-only triggers present");
            return;
        }
        log.error("[AUDIT] audit_log is NOT protected against UPDATE/DELETE, missing triggers {}. Run V101__audit_log_immutable.sql",
                missing);
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("audit_log triggers missing: " + missing + " (see V101__audit_log_immutable.sql)");
        }
    }
}