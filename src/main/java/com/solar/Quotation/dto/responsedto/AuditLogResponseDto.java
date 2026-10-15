package com.solar.Quotation.dto.responsedto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** Partner users get only: action, time, actorName (+ entity info). The rest is filled for Super Admin only. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponseDto {

    private Long id;
    private LocalDateTime createdAt;
    private PartnerRefResponseDto partner;
    private Long actorUserId;
    private String actorName;
    private String actorRole;
    private Long actorPartnerId;
    private String module;
    private String entityType;
    private Long entityId;
    private String entityLabel;
    private String action;
    private Object changes;
    private String ipAddress;
    private String userAgent;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public Long getActorPartnerId() { return actorPartnerId; }
    public void setActorPartnerId(Long actorPartnerId) { this.actorPartnerId = actorPartnerId; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getEntityLabel() { return entityLabel; }
    public void setEntityLabel(String entityLabel) { this.entityLabel = entityLabel; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Object getChanges() { return changes; }
    public void setChanges(Object changes) { this.changes = changes; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
