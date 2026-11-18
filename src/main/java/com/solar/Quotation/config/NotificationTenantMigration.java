package com.solar.Quotation.config;

import com.solar.Quotation.service.PlatformTenant;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Brings an EXISTING database in line with the rule "notification.channel_partner_id is the recipient's own tenant
 * and is NOT NULL". (spring.jpa.hibernate.ddl-auto=update never changes the nullability of an existing column and
 * never touches old rows, so this cannot be left to Hibernate.) Idempotent: after the first run it only counts.
 *
 *   1. rows whose partner is missing, or is not the recipient's own tenant (old REFERRAL_PENDING rows carried the
 *      quotation's partner while the recipient is a Super Admin) -> the recipient's tenant;
 *   2. rows whose recipient no longer exists, and rows whose partner id is NULL or points at no channel_partner row
 *      (when Hibernate adds a NOT NULL column to a filled table MySQL stores 0, not NULL) -> the PLATFORM tenant
 *      (nobody sees them; the cleanup job removes them);
 *   3. ALTER TABLE ... NOT NULL, only when no NULL is left.
 *
 * Same steps as V101__notification_channel_partner_not_null.sql, which is the script to run by hand when
 * ddl-auto is "validate" in production. Never blocks startup: a problem is logged at ERROR with the [NOTIFY] tag.
 * Switch off with app.notifications.migrate-on-startup=false.
 */
@Component
public class NotificationTenantMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationTenantMigration.class);

    /** A row that belongs to no real tenant: NULL, or an id that is not in channel_partner (for example 0). */
    private static final String NO_TENANT =
            "(channel_partner_id IS NULL OR channel_partner_id NOT IN (SELECT id FROM channel_partner))";

    private final EntityManager em;
    private final TransactionTemplate tx;
    private final PlatformTenant platform;
    private final boolean enabled;

    public NotificationTenantMigration(EntityManager em, TransactionTemplate tx, PlatformTenant platform,
                                       @Value("${app.notifications.migrate-on-startup:true}") boolean enabled) {
        this.em = em;
        this.tx = tx;
        this.platform = platform;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[NOTIFY] tenant migration skipped (app.notifications.migrate-on-startup=false)");
            return;
        }
        try {
            tx.executeWithoutResult(status -> migrate());
        } catch (RuntimeException ex) {
            log.error("[NOTIFY] tenant migration failed: notification.channel_partner_id may still allow NULL "
                    + "or hold rows of the wrong tenant. Run V101__notification_channel_partner_not_null.sql by hand.", ex);
        }
    }

    void migrate() {
        int realigned = em.createNativeQuery(
                        "UPDATE notification n JOIN app_user u ON u.id = n.recipient_user_id "
                                + "SET n.channel_partner_id = u.channel_partner_id "
                                + "WHERE n.channel_partner_id IS NULL OR n.channel_partner_id <> u.channel_partner_id")
                .executeUpdate();

        long orphans = count("SELECT COUNT(*) FROM notification WHERE " + NO_TENANT);
        if (orphans > 0) {
            Long platformId = platform.id();
            em.createNativeQuery("UPDATE notification SET channel_partner_id = :p WHERE " + NO_TENANT)
                    .setParameter("p", platformId).executeUpdate();
        }

        boolean nullable = count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'notification' AND column_name = 'channel_partner_id' AND is_nullable = 'YES'") > 0;
        boolean altered = false;
        if (nullable) {
            if (count("SELECT COUNT(*) FROM notification WHERE channel_partner_id IS NULL") == 0) {
                // DDL: MySQL commits implicitly, so this is the last statement of the transaction.
                em.createNativeQuery("ALTER TABLE notification MODIFY channel_partner_id BIGINT NOT NULL").executeUpdate();
                altered = true;
            } else {
                log.error("[NOTIFY] tenant migration: NULL channel_partner_id rows remain, column NOT changed to NOT NULL");
            }
        }

        if (realigned > 0 || orphans > 0 || altered) {
            log.info("[NOTIFY] tenant migration realigned={} orphansToPlatform={} notNullApplied={}", realigned, orphans, altered);
        } else {
            log.debug("[NOTIFY] tenant migration: nothing to do");
        }
    }

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}