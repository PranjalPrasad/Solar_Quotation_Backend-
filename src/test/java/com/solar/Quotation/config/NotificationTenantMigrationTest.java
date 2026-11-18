package com.solar.Quotation.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.service.PlatformTenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The startup step: realign, orphans -> PLATFORM, then NOT NULL only when no NULL is left. */
class NotificationTenantMigrationTest {

    private static final String NULL_COUNT = "SELECT COUNT(*) FROM notification WHERE channel_partner_id IS NULL";

    private final EntityManager em = mock(EntityManager.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final PlatformTenant platform = mock(PlatformTenant.class);
    private final List<String> executed = new ArrayList<>();
    private final List<Object> platformParams = new ArrayList<>();

    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        when(platform.id()).thenReturn(1L);
        logger = (Logger) LoggerFactory.getLogger(NotificationTenantMigration.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logs);
    }

    /** An EntityManager whose COUNT(*) answers come from the given function of the SQL text; every statement is recorded. */
    private void database(ToLongFunction<String> counts) {
        when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenAnswer(p -> {
                platformParams.add(p.getArgument(1));
                return q;
            });
            when(q.executeUpdate()).thenAnswer(x -> {
                executed.add(sql);
                return 0;
            });
            when(q.getSingleResult()).thenAnswer(x -> counts.applyAsLong(sql));
            return q;
        });
    }

    private NotificationTenantMigration migration(boolean enabled) {
        return new NotificationTenantMigration(em, tx, platform, enabled);
    }

    private boolean ran(String fragment) {
        return executed.stream().anyMatch(s -> s.contains(fragment));
    }

    @Test
    void orphans_goToThePlatformTenant_thenTheColumnBecomesNotNull() {
        database(sql -> sql.contains("information_schema") ? 1                // column is still nullable
                : sql.contains("NOT IN") ? 3                                   // 3 rows with no real tenant
                : 0);                                                          // none left with NULL afterwards

        migration(true).migrate();

        assertTrue(ran("SET channel_partner_id = :p"));
        assertEquals(List.of(1L), platformParams);                             // the PLATFORM tenant id
        assertTrue(ran("ALTER TABLE notification MODIFY channel_partner_id BIGINT NOT NULL"));
    }

    @Test
    void whenNullRowsRemain_theColumnIsLeftAlone_andItIsLoggedAtError() {
        database(sql -> sql.contains("information_schema") ? 1 : sql.equals(NULL_COUNT) ? 2 : 0);

        migration(true).migrate();

        assertFalse(ran("ALTER TABLE"));
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("[NOTIFY]")));
    }

    @Test
    void anAlreadyMigratedDatabase_isNotTouchedAgain() {
        database(sql -> 0);                                                    // column NOT NULL, no orphans

        migration(true).migrate();

        assertFalse(ran("ALTER TABLE"));
        assertFalse(ran("SET channel_partner_id = :p"));
    }

    @Test
    void canBeSwitchedOff_andThenTouchesNothing() {
        migration(false).run(mock(ApplicationArguments.class));

        verifyNoInteractions(em, tx);
    }

    @Test
    void aFailingMigration_isLoggedAtError_andNeverBlocksStartup() {
        doThrow(new IllegalStateException("db down")).when(tx).executeWithoutResult(any());

        assertDoesNotThrow(() -> migration(true).run(mock(ApplicationArguments.class)));

        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("[NOTIFY] tenant migration failed")));
    }
}