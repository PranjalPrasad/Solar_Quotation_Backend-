package com.solar.Quotation.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.repository.NotificationCleanupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cleanup runs PER PARTNER, in batches, and one broken partner never stops the others. */
class NotificationCleanupTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final Long P = 7L;

    private final NotificationCleanupRepository repository = mock(NotificationCleanupRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    private Logger jobLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // run every batch's callback directly, as a real TransactionTemplate would inside its transaction
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        jobLogger = (Logger) LoggerFactory.getLogger(NotificationCleanupJob.class);
        logs = new ListAppender<>();
        logs.start();
        jobLogger.addAppender(logs);
    }

    @AfterEach
    void detach() {
        jobLogger.detachAppender(logs);
    }

    // ---------------------------------------------------------------- processor (one partner, batches)

    @Test
    void removesOldRowsInBatches_untilAShortBatch() {
        NotificationCleanupProcessor processor = new NotificationCleanupProcessor(repository, tx, 2, 10);
        when(repository.oldestIdsForPartner(eq(P), eq(CUTOFF), any()))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of(3L));
        when(repository.deleteBatch(P, List.of(1L, 2L))).thenReturn(2);
        when(repository.deleteBatch(P, List.of(3L))).thenReturn(1);

        NotificationCleanupProcessor.PartnerResult r = processor.purgePartner(P, CUTOFF);

        assertEquals(3, r.removed());
        assertEquals(2, r.batches());
        assertFalse(r.capped());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).oldestIdsForPartner(eq(P), eq(CUTOFF), page.capture());
        assertEquals(2, page.getValue().getPageSize());          // batch size, never "all rows"
    }

    @Test
    void stopsAtTheBatchCap_andSaysSo_soTheNextRunContinues() {
        NotificationCleanupProcessor processor = new NotificationCleanupProcessor(repository, tx, 2, 2);
        when(repository.oldestIdsForPartner(eq(P), eq(CUTOFF), any())).thenReturn(List.of(1L, 2L));
        when(repository.deleteBatch(eq(P), any())).thenReturn(2);

        NotificationCleanupProcessor.PartnerResult r = processor.purgePartner(P, CUTOFF);

        assertEquals(4, r.removed());
        assertEquals(2, r.batches());
        assertTrue(r.capped());
    }

    @Test
    void nothingOld_nothingDeleted() {
        NotificationCleanupProcessor processor = new NotificationCleanupProcessor(repository, tx, 2, 10);
        when(repository.oldestIdsForPartner(eq(P), eq(CUTOFF), any())).thenReturn(List.of());

        NotificationCleanupProcessor.PartnerResult r = processor.purgePartner(P, CUTOFF);

        assertEquals(0, r.removed());
        verify(repository, never()).deleteBatch(any(), any());
    }

    // ---------------------------------------------------------------- job (all partners)

    @Test
    void aFailingPartner_isLoggedWithItsId_andTheOthersAreStillCleaned() {
        NotificationCleanupProcessor processor = mock(NotificationCleanupProcessor.class);
        NotificationCleanupJob job = new NotificationCleanupJob(repository, processor);
        when(repository.partnerIdsWithRowsOlderThan(CUTOFF)).thenReturn(List.of(1L, 2L, 3L));
        when(processor.purgePartner(1L, CUTOFF)).thenReturn(new NotificationCleanupProcessor.PartnerResult(1L, 5, 1, false));
        when(processor.purgePartner(2L, CUTOFF)).thenThrow(new IllegalStateException("lock timeout"));
        when(processor.purgePartner(3L, CUTOFF)).thenReturn(new NotificationCleanupProcessor.PartnerResult(3L, 7, 1, false));

        NotificationCleanupJob.RunSummary s = job.runFor(CUTOFF);

        assertEquals(2, s.partnersOk());
        assertEquals(1, s.partnersFailed());
        assertEquals(12, s.removed());
        verify(processor).purgePartner(3L, CUTOFF);               // partner 3 ran after partner 2 failed
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("[NOTIFY] cleanup failed partnerId=2")));
    }

    @Test
    void ifThePartnerListCannotBeRead_theRunIsAbortedAtError_withoutThrowing() {
        NotificationCleanupProcessor processor = mock(NotificationCleanupProcessor.class);
        NotificationCleanupJob job = new NotificationCleanupJob(repository, processor);
        when(repository.partnerIdsWithRowsOlderThan(CUTOFF)).thenThrow(new IllegalStateException("db down"));

        NotificationCleanupJob.RunSummary s = job.runFor(CUTOFF);

        assertEquals(1, s.partnersFailed());
        verify(processor, never()).purgePartner(any(), any());
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("[NOTIFY] cleanup could not list partners")));
    }
}