package com.solar.Quotation.dto.responsedto;

public class NotificationResponseDto {

    private Long id;
    private String type;
    private String severity;
    private String title;
    private String message;
    private String entityType;
    private Long entityId;
    private Long partnerId;
    private boolean read;
    /** ISO-8601 UTC instants (e.g. 2026-10-27T09:15:00Z) so the browser can show "10 min ago" in local time. */
    private String readAt;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public String getReadAt() { return readAt; }
    public void setReadAt(String readAt) { this.readAt = readAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
