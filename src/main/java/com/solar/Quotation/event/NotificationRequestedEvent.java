package com.solar.Quotation.event;

import java.util.List;

public record NotificationRequestedEvent(List<NotificationDraft> drafts) {
}
