package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.NotificationFilterRequestDto;
import com.solar.Quotation.dto.responsedto.NotificationResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.UnreadCountResponseDto;

public interface NotificationService {

    PageResponse<NotificationResponseDto> list(NotificationFilterRequestDto filter);

    UnreadCountResponseDto unreadCount();

    /** 404 when the notification does not exist or belongs to someone else. Returns the new unread count. */
    UnreadCountResponseDto markRead(Long id);

    UnreadCountResponseDto markAllRead();

    /** Soft delete. 404 when it is not the caller's. */
    UnreadCountResponseDto delete(Long id);
}
