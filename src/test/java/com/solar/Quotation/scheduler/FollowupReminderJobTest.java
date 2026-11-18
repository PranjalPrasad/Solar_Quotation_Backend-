package com.solar.Quotation.scheduler;

import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The job's own promise: one partner failing never stops the others, and the run is summarised. */
class FollowupReminderJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    private final ReminderQueryRepository queries = mock(ReminderQueryRepository.class);
    private final FollowupReminderProcessor processor = mock(FollowupReminderProcessor.class);
    private final FollowupReminderJob job = new FollowupReminderJob(queries, processor);

    @Test
    void failureInOnePartner_doesNotStopTheOthers() {
        when(queries.partnersWithDueFollowups(FollowupStatus.PENDING, RecordStatus.ACTIVE, TODAY))
                .thenReturn(List.of(1L, 2L, 3L));
        when(processor.processPartner(1L, TODAY)).thenReturn(new FollowupReminderProcessor.PartnerResult(1L, 4, 0));
        when(processor.processPartner(2L, TODAY)).thenThrow(new IllegalStateException("db down for this partner"));
        when(processor.processPartner(3L, TODAY)).thenReturn(new FollowupReminderProcessor.PartnerResult(3L, 2, 1));

        FollowupReminderJob.RunSummary s = job.runFor(TODAY);

        verify(processor).processPartner(1L, TODAY);
        verify(processor).processPartner(2L, TODAY);
        verify(processor).processPartner(3L, TODAY);       // still reached after partner 2 blew up
        assertEquals(2, s.partnersOk());
        assertEquals(1, s.partnersFailed());
        assertEquals(6, s.processed());
    }

    @Test
    void cannotListPartners_runEndsCleanly_withoutThrowing() {
        when(queries.partnersWithDueFollowups(any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        FollowupReminderJob.RunSummary s = job.runFor(TODAY);

        assertEquals(1, s.partnersFailed());
        assertEquals(0, s.processed());
        verify(processor, never()).processPartner(any(), any());
    }

    @Test
    void noDueFollowups_isAnEmptyRun() {
        when(queries.partnersWithDueFollowups(eq(FollowupStatus.PENDING), eq(RecordStatus.ACTIVE), any()))
                .thenReturn(List.of());
        FollowupReminderJob.RunSummary s = job.runFor(TODAY);
        assertEquals(0, s.partnersOk() + s.partnersFailed() + s.processed());
    }
}