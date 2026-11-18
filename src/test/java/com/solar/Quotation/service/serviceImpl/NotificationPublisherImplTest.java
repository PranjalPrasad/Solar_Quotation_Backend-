package com.solar.Quotation.service.serviceImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.NotificationSeverity;
import com.solar.Quotation.Enum.NotificationType;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.Enum.ReferralStatus;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.event.NotificationDraft;
import com.solar.Quotation.event.NotificationRequestedEvent;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.NotificationRecipientRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.PlatformTenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tenant rules of the publisher, and "a notification problem is logged at ERROR and never reaches the caller". */
class NotificationPublisherImplTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NotificationRecipientRepository recipients = mock(NotificationRecipientRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final PlatformTenant platform = mock(PlatformTenant.class);
    private final PartnerRefProvider partnerRefs = mock(PartnerRefProvider.class);
    private final NotificationPublisherImpl publisher =
            new NotificationPublisherImpl(events, recipients, customers, platform, partnerRefs);

    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(NotificationPublisherImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        when(platform.id()).thenReturn(PLATFORM);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(logs);
        TenantContext.clear();
    }

    private boolean errorLogged(String fragment) {
        return logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("[NOTIFY]")
                && e.getFormattedMessage().contains(fragment));
    }

    private static Quotation acceptedReferredQuotation() {
        Quotation q = new Quotation();
        q.setId(5L);
        q.setChannelPartnerId(A);
        q.setCode("Q-5");
        q.setCustomerId(9L);
        q.setStatus(QuotationStatus.ACCEPTED);
        q.setReferred(true);
        q.setReferralStatus(ReferralStatus.PENDING);
        q.setReferralAmount(new BigDecimal("5000"));
        q.setReferredBy("Ravi");
        return q;
    }

    @Test
    void referralPending_goesToPlatformSuperAdmins_underThePlatformTenant_notThePartners() {
        when(recipients.activeUserIdsByPartnerAndRole(A, "CHANNEL_PARTNER_ADMIN", RecordStatus.ACTIVE)).thenReturn(List.of(20L, 21L));
        when(recipients.activeUserIdsByPartnerAndRole(PLATFORM, "SUPER_ADMIN", RecordStatus.ACTIVE)).thenReturn(List.of(1L, 2L));
        when(partnerRefs.ref(A)).thenReturn(new PartnerRefResponseDto(A, "SUN", "Sunrise Solar"));
        TenantContext.set(new CurrentUser(20L, "Admin One", 10L, "CHANNEL_PARTNER_ADMIN", DataScope.PARTNER, A));

        publisher.quotationStatusChanged(acceptedReferredQuotation(), QuotationStatus.PENDING);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(captor.capture());
        List<NotificationDraft> all = captor.getAllValues().stream()
                .flatMap(e -> ((NotificationRequestedEvent) e).drafts().stream()).toList();

        List<NotificationDraft> referral = all.stream().filter(d -> d.type() == NotificationType.REFERRAL_PENDING).toList();
        assertEquals(Set.of(1L, 2L), referral.stream().map(NotificationDraft::recipientUserId).collect(Collectors.toSet()));
        for (NotificationDraft d : referral) {
            assertEquals(PLATFORM, d.partnerId());                       // recipient's own tenant
            assertEquals("REFERRAL_PENDING:5", d.dedupeKey());
            assertEquals("QUOTATION", d.entityType());
            assertEquals(5L, d.entityId());
            assertEquals(NotificationSeverity.INFO, d.severity());
            assertTrue(d.message().contains("Sunrise Solar"));           // the partner is named, not the owner
        }

        List<NotificationDraft> accepted = all.stream().filter(d -> d.type() == NotificationType.QUOTATION_ACCEPTED).toList();
        assertEquals(List.of(21L), accepted.stream().map(NotificationDraft::recipientUserId).toList());   // actor 20 excluded
        for (NotificationDraft d : accepted) assertEquals(A, d.partnerId());
    }

    @Test
    void publishFailure_isLoggedAtError_andNeverThrown() {
        doThrow(new IllegalStateException("bus down")).when(events).publishEvent(any(Object.class));
        NotificationDraft d = new NotificationDraft(21L, A, NotificationType.SYSTEM, NotificationSeverity.INFO,
                "t", "m", null, null, null);

        assertDoesNotThrow(() -> publisher.publish(List.of(d)));

        assertTrue(errorLogged("publish failed"));
    }

    @Test
    void buildFailure_isLoggedAtError_andNeverThrown() {
        when(recipients.activeUserIdsByPartnerAndRole(any(), any(), any())).thenThrow(new RuntimeException("db down"));
        Customer c = new Customer();
        c.setId(3L);
        c.setChannelPartnerId(A);
        c.setName("Meera");

        assertDoesNotThrow(() -> publisher.customerCreated(c));

        assertTrue(errorLogged("could not build"));
        verifyNoInteractions(events);
    }

    @Test
    void aDraftWithoutATenant_isNeverPublished_andIsLoggedAtError() {
        Quotation q = new Quotation();
        q.setId(8L);
        q.setChannelPartnerId(null);
        q.setCode("Q-8");

        assertDoesNotThrow(() -> publisher.quotationCreated(q));

        assertTrue(errorLogged("no partnerId"));
        verifyNoInteractions(events);
    }
}