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
    private Long tenantId;
    private Long actorTenantId;
    private String module;
    private String entityType;
    private Long entityId;
    private String entityLabel;
    private String action;
    private Object changes;
    /** Masked state before / after. Only on the single-entry endpoint (GET /api/audit/{id}), never in lists. */
    private Object previousData;
    private Object newData;
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
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getActorTenantId() { return actorTenantId; }
    public void setActorTenantId(Long actorTenantId) { this.actorTenantId = actorTenantId; }
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
    public Object getPreviousData() { return previousData; }
    public void setPreviousData(Object previousData) { this.previousData = previousData; }
    public Object getNewData() { return newData; }
    public void setNewData(Object newData) { this.newData = newData; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}