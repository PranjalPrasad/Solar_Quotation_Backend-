package com.solar.Quotation.scheduler;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Expiry job: per partner, in batches, one failing partner or row does not stop the rest, audited as SYSTEM. */
class QuotationExpiryJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate UNTIL = TODAY.plusDays(3);

    private final ReminderQueryRepository queries = mock(ReminderQueryRepository.class);
    private final NotificationPublisher publisher = mock(NotificationPublisher.class);
    private final AuditService audit = mock(AuditService.class);

    /** [quotationId, partnerId, code, createdBy, validUntil, customerName] */
    private static Object[] row(long id, long partner) {
        return new Object[]{id, partner, "SQ-" + id, 5L, TODAY.plusDays(1), "Asha"};
    }

    @Test
    void processor_readsInKeysetBatches_isolatesRowFailures_andAuditsAsSystem() {
        QuotationExpiryProcessor p = new QuotationExpiryProcessor(queries, publisher, audit, 2);
        when(queries.expiringQuotationsForPartner(eq(12L), eq(QuotationStatus.PENDING), eq(TODAY), eq(UNTIL), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(row(1, 12), row(2, 12)));
        when(queries.expiringQuotationsForPartner(eq(12L), any(), any(), any(), eq(2L), any(Pageable.class)))
                .thenReturn(List.of(row(3, 12)));
        doThrow(new IllegalStateException("boom")).when(publisher)
                .quotationExpiring(eq(2L), any(), any(), any(), any(), any(), any());

        QuotationExpiryProcessor.PartnerResult r = p.processPartner(12L, TODAY, UNTIL);

        assertEquals(2, r.processed());
        assertEquals(1, r.failed());
        verify(publisher).quotationExpiring(eq(1L), eq(12L), eq("SQ-1"), any(), any(), any(), eq(TODAY));
        verify(publisher).quotationExpiring(eq(3L), eq(12L), eq("SQ-3"), any(), any(), any(), eq(TODAY));
        // actor null = SYSTEM, written under the partner
        verify(audit).recordAs(isNull(), eq(AuditAction.CREATE), eq("QUOTATION"), eq("QuotationExpiryRun"),
                any(), any(), eq(12L), anyList());
    }

    @Test
    void processor_neverNotifiesForARowOfAnotherPartner() {
        QuotationExpiryProcessor p = new QuotationExpiryProcessor(queries, publisher, audit, 10);
        when(queries.expiringQuotationsForPartner(eq(12L), any(), any(), any(), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(row(1, 13)));                       // should be impossible; must not cross tenants

        QuotationExpiryProcessor.PartnerResult r = p.processPartner(12L, TODAY, UNTIL);

        assertEquals(0, r.processed());
        assertEquals(1, r.failed());
        verify(publisher, never()).quotationExpiring(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void job_aFailingPartnerIsAuditedAndTheNextPartnerStillRuns() {
        QuotationExpiryProcessor processor = mock(QuotationExpiryProcessor.class);
        when(queries.partnersWithExpiringQuotations(eq(QuotationStatus.PENDING), eq(RecordStatus.ACTIVE), eq(TODAY), any()))
                .thenReturn(List.of(11L, 12L, 13L));
        when(processor.processPartner(eq(11L), any(), any())).thenReturn(new QuotationExpiryProcessor.PartnerResult(11L, 2, 0));
        when(processor.processPartner(eq(12L), any(), any())).thenThrow(new IllegalStateException("db down"));
        when(processor.processPartner(eq(13L), any(), any())).thenReturn(new QuotationExpiryProcessor.PartnerResult(13L, 1, 1));

        QuotationExpiryJob.RunSummary s = new QuotationExpiryJob(queries, processor).runFor(TODAY);

        assertEquals(2, s.partnersOk());
        assertEquals(1, s.partnersFailed());
        assertEquals(3, s.processed());
        assertEquals(1, s.reminderFailures());
        verify(processor).auditPartnerFailure(eq(12L), eq(TODAY), any(RuntimeException.class));
        verify(processor).processPartner(eq(13L), any(), any());
    }

    @Test
    void job_cannotListPartners_abortsWithoutThrowing() {
        when(queries.partnersWithExpiringQuotations(any(), any(), any(), any())).thenThrow(new IllegalStateException("db"));
        QuotationExpiryJob.RunSummary s = new QuotationExpiryJob(queries, mock(QuotationExpiryProcessor.class)).runFor(TODAY);
        assertEquals(1, s.partnersFailed());
        assertEquals(0, s.processed());
    }

    @Test
    void processor_aFailedPartnerAuditRowCarriesTheExceptionClassOnly() {
        QuotationExpiryProcessor p = new QuotationExpiryProcessor(queries, publisher, audit, 10);
        p.auditPartnerFailure(12L, TODAY, new IllegalStateException("secret detail"));
        verify(audit).recordAs(isNull(), eq(AuditAction.CREATE), eq("QUOTATION"), eq("QuotationExpiryRun"), any(), any(), eq(12L),
                argThat((List<Map<String, String>> changes) ->
                        changes.stream().anyMatch(m -> "error".equals(m.get("field")) && "IllegalStateException".equals(m.get("new")))
                                && changes.stream().noneMatch(m -> String.valueOf(m.get("new")).contains("secret"))));
    }
}