package com.solar.Quotation.entity;


import com.solar.Quotation.Enum.NotificationSeverity;
import com.solar.Quotation.Enum.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_dedupe",
                columnNames = {"recipient_user_id", "dedupe_key"}),
        indexes = @Index(name = "idx_notification_recipient",
                columnList = "recipient_user_id,deleted,read_at,created_at"))
public class Notification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private Long recipientUserId;

    @Column(name = "channel_partner_id")
    private Long channelPartnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** QUOTATION, CUSTOMER, PROJECT ... the frontend uses this + entityId to open the right page. */
    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** null = unread. Stored in UTC like every other timestamp in this project. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** Same key for the same user is stored once (scheduler re-runs / double clicks are harmless). */
    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long recipientUserId) { this.recipientUserId = recipientUserId; }
    public Long getChannelPartnerId() { return channelPartnerId; }
    public void setChannelPartnerId(Long channelPartnerId) { this.channelPartnerId = channelPartnerId; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public NotificationSeverity getSeverity() { return severity; }
    public void setSeverity(NotificationSeverity severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
}
