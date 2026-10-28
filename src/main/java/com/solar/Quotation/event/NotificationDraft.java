package com.solar.Quotation.event;

import com.solar.Quotation.Enum.NotificationSeverity;
import com.solar.Quotation.Enum.NotificationType;

public record NotificationDraft(Long recipientUserId,
                                Long partnerId,
                                NotificationType type,
                                NotificationSeverity severity,
                                String title,
                                String message,
                                String entityType,
                                Long entityId,
                                String dedupeKey) {
}
