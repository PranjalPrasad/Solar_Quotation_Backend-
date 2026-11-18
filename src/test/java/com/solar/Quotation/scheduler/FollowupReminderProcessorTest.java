package com.solar.Quotation.scheduler;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Per-partner processing: batches, partnerId on notifications and audits, and failure handling. */
class FollowupReminderProcessorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final Long P = 7L;

    private final ReminderQueryRepository queries = mock(ReminderQueryRepository.class);
    private final NotificationPublisher publisher = mock(NotificationPublisher.class);
    private final AuditService audit = mock(AuditService.class);
    private final FollowupReminderProcessor processor = new FollowupReminderProcessor(queries, publisher, audit, 2);

    private static Object[] row(long followupId, long partnerId, long customerId) {
        return new Object[]{followupId, partnerId, customerId, 5L, TODAY.minusDays(1), "Customer " + customerId};
    }

    @Test
    void readsInKeysetBatches_andRaisesEveryReminderUnderTheSamePartner() {
        List<Long> afterIds = new ArrayList<>();
        when(queries.duePendingFollowupsForPartner(eq(P), eq(FollowupStatus.PENDING), eq(TODAY), anyLong(), any()))
                .thenAnswer(inv -> {
                    long after = inv.getArgument(3);
                    afterIds.add(after);
                    if (after == 0L) return List.of(row(10, P, 100), row(11, P, 101));   // full batch -> read again
                    if (after == 11L) return List.of(row(12, P, 102));                   // short batch -> done
                    return List.of();
                });

        FollowupReminderProcessor.PartnerResult r = processor.processPartner(P, TODAY);

        assertEquals(List.of(0L, 11L), afterIds);          // keyset: next batch starts after the last id seen
        assertEquals(3, r.processed());
        assertEquals(0, r.failed());
        for (long id : new long[]{10, 11, 12}) {
            verify(publisher).followupReminder(eq(id), eq(P), anyLong(), eq(5L), any(), any(), eq(TODAY));
        }
    }

    @Test
    void auditRow_isWrittenUnderThePartner_asSystem_withCounts() {
        when(queries.duePendingFollowupsForPartner(eq(P), any(), any(), anyLong(), any()))
                .thenReturn(List.of(row(10, P, 100)));

        processor.processPartner(P, TODAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> changes = ArgumentCaptor.forClass(List.class);
        verify(audit).recordAs(isNull(), eq(AuditAction.CREATE), eq("CUSTOMER"), eq("FollowupReminderRun"),
                isNull(), eq(TODAY.toString()), eq(P), changes.capture());
        assertEquals("1", changes.getValue().stream()
                .filter(m -> "remindersRaised".equals(m.get("field"))).findFirst().orElseThrow().get("new"));
    }

    @Test
    void oneBadRow_isCounted_andTheNextRowStillGoesOut() {
        when(queries.duePendingFollowupsForPartner(eq(P), any(), any(), anyLong(), any()))
                .thenReturn(List.of(row(10, P, 100), row(11, P, 101)));
        doThrow(new IllegalStateException("boom")).when(publisher)
                .followupReminder(eq(10L), any(), any(), any(), any(), any(), any());

        FollowupReminderProcessor.PartnerResult r = processor.processPartner(P, TODAY);

        assertEquals(1, r.processed());
        assertEquals(1, r.failed());
        verify(publisher).followupReminder(eq(11L), eq(P), eq(101L), any(), any(), any(), eq(TODAY));
    }

    @Test
    void aRowOfAnotherPartner_isNeverNotified() {
        when(queries.duePendingFollowupsForPartner(eq(P), any(), any(), anyLong(), any()))
                .thenReturn(List.of(row(10, 99L, 100)));          // would be a query bug; must not cross tenants

        FollowupReminderProcessor.PartnerResult r = processor.processPartner(P, TODAY);

        assertEquals(0, r.processed());
        assertEquals(1, r.failed());
        verify(publisher, never()).followupReminder(any(), any(), any(), any(), any(), any(), any());
        verify(audit, never()).recordAs(any(), any(), any(), any(), any(), any(), any(), any(java.util.List.class));
    }

    @Test
    void auditFailure_doesNotHideTheRemindersAlreadyRaised() {
        when(queries.duePendingFollowupsForPartner(eq(P), any(), any(), anyLong(), any()))
                .thenReturn(List.of(row(10, P, 100)));
        doThrow(new IllegalStateException("audit db")).when(audit)
                .recordAs(any(), any(), any(), any(), any(), any(), any(), any(java.util.List.class));

        assertEquals(1, processor.processPartner(P, TODAY).processed());
    }

    @Test
    void batchReadFailure_propagates_soTheJobCanLogItWithThePartnerId() {
        when(queries.duePendingFollowupsForPartner(eq(P), any(), any(), anyLong(), any()))
                .thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> processor.processPartner(P, TODAY));
        verify(audit, times(0)).recordAs(any(), any(), any(), any(), any(), any(), any(), any(java.util.List.class));
    }
}