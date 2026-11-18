package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.NotificationSeverity;
import com.solar.Quotation.Enum.NotificationType;
import com.solar.Quotation.dto.requestdto.NotificationFilterRequestDto;
import com.solar.Quotation.dto.responsedto.NotificationResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.UnreadCountResponseDto;
import com.solar.Quotation.entity.Notification;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.NotificationRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The bell is "my notifications": every call is pinned to the caller's OWN tenant and user, never to a request value. */
class NotificationServiceImplTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long SUPER_ADMIN_ID = 2L;
    private static final Long PARTNER_USER_ID = 20L;

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(repository, new TenantResolver());

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    private static void signInPartnerUser() {
        TenantContext.set(new CurrentUser(PARTNER_USER_ID, "Partner Admin", 10L, "CHANNEL_PARTNER_ADMIN", DataScope.PARTNER, A));
    }

    private static CurrentUser superAdminViewing(Long viewPartnerId) {
        CurrentUser u = new CurrentUser(SUPER_ADMIN_ID, "Super Admin", 1L, "SUPER_ADMIN", DataScope.ALL, PLATFORM);
        u.setViewPartnerId(viewPartnerId);
        return u;
    }

    private static Notification row(long id, Long partnerId) {
        Notification n = new Notification();
        n.setId(id);
        n.setChannelPartnerId(partnerId);
        n.setRecipientUserId(PARTNER_USER_ID);
        n.setType(NotificationType.SYSTEM);
        n.setSeverity(NotificationSeverity.INFO);
        n.setTitle("t");
        n.setMessage("m");
        return n;
    }

    @Test
    void list_isPinnedToTheCallersTenantAndUser() {
        signInPartnerUser();
        when(repository.search(eq(A), eq(PARTNER_USER_ID), eq(1), isNull(), any())).thenReturn(new PageImpl<>(List.of(row(7L, A))));
        NotificationFilterRequestDto f = new NotificationFilterRequestDto();
        f.setUnreadOnly(true);

        PageResponse<NotificationResponseDto> page = service.list(f);

        assertEquals(1, page.getContent().size());
        assertEquals(A, page.getContent().get(0).getPartnerId());
    }

    @Test
    void list_ofASuperAdmin_isThePlatformTenant_evenWhileViewingAPartner() {
        TenantContext.set(superAdminViewing(A));       // ?viewPartnerId=12 on a GET
        when(repository.search(eq(PLATFORM), eq(SUPER_ADMIN_ID), eq(0), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(new NotificationFilterRequestDto());

        verify(repository).search(eq(PLATFORM), eq(SUPER_ADMIN_ID), eq(0), isNull(), any());
        verify(repository, never()).search(eq(A), any(), any(Integer.class), any(), any());
    }

    @Test
    void unreadCount_isPinnedToTheCallersTenantAndUser() {
        signInPartnerUser();
        when(repository.countUnread(A, PARTNER_USER_ID)).thenReturn(4L);

        assertEquals(4L, service.unreadCount().getUnread());
    }

    @Test
    void markRead_ofARowThatIsNotTheCallers_is404() {
        signInPartnerUser();
        when(repository.markRead(eq(7L), eq(A), eq(PARTNER_USER_ID), any())).thenReturn(0);

        assertThrows(ResourceNotFoundException.class, () -> service.markRead(7L));
        verify(repository, never()).countUnread(any(), any());
    }

    @Test
    void markRead_returnsTheNewUnreadCount() {
        signInPartnerUser();
        when(repository.markRead(eq(7L), eq(A), eq(PARTNER_USER_ID), any())).thenReturn(1);
        when(repository.countUnread(A, PARTNER_USER_ID)).thenReturn(2L);

        assertEquals(2L, service.markRead(7L).getUnread());
    }

    @Test
    void markAllRead_isPinnedToTheCallersTenantAndUser() {
        signInPartnerUser();
        when(repository.markAllRead(eq(A), eq(PARTNER_USER_ID), any())).thenReturn(3);
        when(repository.countUnread(A, PARTNER_USER_ID)).thenReturn(0L);

        UnreadCountResponseDto r = service.markAllRead();

        assertEquals(0L, r.getUnread());
        verify(repository).markAllRead(eq(A), eq(PARTNER_USER_ID), any());
    }

    @Test
    void delete_ofARowThatIsNotTheCallers_is404_andNothingIsSaved() {
        signInPartnerUser();
        when(repository.findByIdAndChannelPartnerIdAndRecipientUserIdAndDeletedFalse(7L, A, PARTNER_USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(7L));
        verify(repository, never()).save(any());
    }

    @Test
    void delete_isASoftDelete() {
        signInPartnerUser();
        Notification n = row(7L, A);
        when(repository.findByIdAndChannelPartnerIdAndRecipientUserIdAndDeletedFalse(7L, A, PARTNER_USER_ID)).thenReturn(Optional.of(n));

        service.delete(7L);

        assertTrue(n.isDeleted());
        verify(repository).save(n);
    }

    @Test
    void anUnknownTypeFilter_isAValidationError() {
        signInPartnerUser();
        NotificationFilterRequestDto f = new NotificationFilterRequestDto();
        f.setType("NOT_A_TYPE");

        assertThrows(BusinessException.class, () -> service.list(f));
    }

    @Test
    void withoutASignedInUser_nothingIsReadOrChanged() {
        assertThrows(ForbiddenException.class, () -> service.unreadCount());
        assertThrows(ForbiddenException.class, () -> service.markAllRead());
        verifyNoInteractions(repository);
    }
}