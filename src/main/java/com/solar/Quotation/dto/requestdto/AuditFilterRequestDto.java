package com.solar.Quotation.dto.requestdto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class AuditFilterRequestDto extends PageFilterRequestDto {

    /** "PLATFORM" or a numeric partner id. Wins over partnerId. Empty = all tenants. */
    private String partner;
    private Long userId;
    private String module;
    private String action;
    private String entityType;
    private Long entityId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    public String getPartner() { return partner; }
    public void setPartner(String partner) { this.partner = partner; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
}