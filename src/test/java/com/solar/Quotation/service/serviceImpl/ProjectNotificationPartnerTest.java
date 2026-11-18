package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.NotificationType;
import com.solar.Quotation.Enum.ProjectStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.event.NotificationDraft;
import com.solar.Quotation.event.NotificationRequestedEvent;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.NotificationRecipientRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.PlatformTenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Project status notifications carry the PROJECT's partnerId and go only to that partner's admins + the creator. */
class ProjectNotificationPartnerTest {

    private static final Long A = 12L;
    private static final Long B = 13L;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    private static NotificationPublisherImpl publisher(ApplicationEventPublisher events,
                                                       NotificationRecipientRepository recipients) {
        return new NotificationPublisherImpl(events, recipients, mock(CustomerRepository.class),
                mock(PlatformTenant.class), mock(PartnerRefProvider.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void draftsCarryTheProjectsPartnerId_andRecipientsAreOnlyThatPartnersUsers() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        NotificationRecipientRepository recipients = mock(NotificationRecipientRepository.class);
        when(recipients.activeUserIdsByPartnerAndRole(A, "CHANNEL_PARTNER_ADMIN", RecordStatus.ACTIVE)).thenReturn(List.of(20L, 21L));
        NotificationPublisherImpl publisher = publisher(events, recipients);

        TenantContext.set(new CurrentUser(21L, "Admin Two", 10L, "CHANNEL_PARTNER_ADMIN", DataScope.PARTNER, A));
        Project p = new Project();
        p.setId(77L);
        p.setChannelPartnerId(A);
        p.setCreatedBy(30L);
        p.setName("Roof");
        p.setStatus(ProjectStatus.IN_PROGRESS);

        publisher.projectStatusChanged(p, ProjectStatus.PLANNING);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        List<NotificationDraft> drafts = ((NotificationRequestedEvent) captor.getValue()).drafts();

        assertFalse(drafts.isEmpty());
        for (NotificationDraft d : drafts) {
            assertEquals(A, d.partnerId());
            assertEquals(NotificationType.PROJECT_STATUS_CHANGED, d.type());
            assertEquals("PROJECT", d.entityType());
            assertEquals(77L, d.entityId());
        }
        List<Long> to = drafts.stream().map(NotificationDraft::recipientUserId).toList();
        assertTrue(to.contains(20L));          // other admin of the partner
        assertTrue(to.contains(30L));          // the project's creator
        assertFalse(to.contains(21L));         // not the person who made the change
        verify(recipients, never()).activeUserIdsByPartnerAndRole(B, "CHANNEL_PARTNER_ADMIN", RecordStatus.ACTIVE);
        // the only recipient lookup is the project's own partner: no "all users with role X" query exists any more
        verify(recipients).activeUserIdsByPartnerAndRole(A, "CHANNEL_PARTNER_ADMIN", RecordStatus.ACTIVE);
        verifyNoMoreInteractions(recipients);
    }

    @Test
    void noNotificationWhenTheStatusDidNotChange() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        NotificationPublisherImpl publisher = publisher(events, mock(NotificationRecipientRepository.class));
        TenantContext.set(new CurrentUser(5L, "X", 10L, "ROLE_X", DataScope.PARTNER, A));
        Project p = new Project();
        p.setId(1L);
        p.setChannelPartnerId(A);
        p.setStatus(ProjectStatus.PLANNING);

        publisher.projectStatusChanged(p, ProjectStatus.PLANNING);

        verify(events, never()).publishEvent(any(Object.class));
    }
}