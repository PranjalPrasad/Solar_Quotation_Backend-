package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One row = one thing that happened. INSERT-ONLY, enforced in three layers:
 *
 *   1. Java:     this class does NOT extend BaseEntity / TenantBaseEntity (no soft-delete flag, no @Version, no
 *                updated_at, no @PreUpdate), has no setters, and is {@link Immutable}: Hibernate never issues an
 *                UPDATE for it, and ignores attempts to modify or remove a loaded instance.
 *   2. Database: BEFORE UPDATE / BEFORE DELETE triggers raise an error (see db/migration/V101__audit_log_immutable.sql).
 *   3. Grants:   the application's DB user gets INSERT and SELECT on audit_log and nothing else.
 *
 * Tenant columns (names kept so existing rows and queries stay valid):
 *   channel_partner_id = TENANT ID       : the tenant whose data was touched.
 *   actor_partner_id   = ACTOR TENANT ID : the tenant the acting user belongs to (Super Admin: the PLATFORM tenant).
 *
 * previous_data / new_data are MASKED JSON snapshots (see AuditMasker). changes_json is the field-level diff,
 * built from the same masked values. Neither is ever written to a log line.
 */
@Entity
@Immutable
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_partner_created", columnList = "channel_partner_id, created_at"),
        @Index(name = "idx_audit_entity", columnList = "channel_partner_id, entity_type, entity_id"),
        @Index(name = "idx_audit_actor_created", columnList = "actor_user_id, created_at"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** TENANT ID. */
    @Column(name = "channel_partner_id", nullable = false, updatable = false)
    private Long tenantId;

    /** ACTOR TENANT ID. Null for SYSTEM rows (scheduler, unknown actor). */
    @Column(name = "actor_partner_id", updatable = false)
    private Long actorTenantId;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_name", length = 150, updatable = false)
    private String actorName;

    @Column(name = "actor_role", length = 60, updatable = false)
    private String actorRole;

    @Column(nullable = false, length = 40, updatable = false)
    private String module;

    @Column(name = "entity_type", nullable = false, length = 60, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(name = "entity_label", length = 150, updatable = false)
    private String entityLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AuditAction action;

    @Column(name = "changes_json", columnDefinition = "TEXT", updatable = false)
    private String changesJson;

    /** Masked state BEFORE the action (null for CREATE and for events without a record). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_data", columnDefinition = "json", updatable = false)
    private String previousData;

    /** Masked state AFTER the action (null for DELETE and for events without a record). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_data", columnDefinition = "json", updatable = false)
    private String newData;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    /** For Hibernate only. */
    protected AuditLog() { }

    private AuditLog(Builder b) {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.tenantId = b.tenantId;
        this.actorTenantId = b.actorTenantId;
        this.actorUserId = b.actorUserId;
        this.actorName = b.actorName;
        this.actorRole = b.actorRole;
        this.module = b.module;
        this.entityType = b.entityType;
        this.entityId = b.entityId;
        this.entityLabel = b.entityLabel;
        this.action = b.action;
        this.changesJson = b.changesJson;
        this.previousData = b.previousData;
        this.newData = b.newData;
        this.ipAddress = b.ipAddress;
        this.userAgent = b.userAgent;
    }

    public static Builder builder() { return new Builder(); }

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getTenantId() { return tenantId; }
    public Long getActorTenantId() { return actorTenantId; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public String getModule() { return module; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getEntityLabel() { return entityLabel; }
    public AuditAction getAction() { return action; }
    public String getChangesJson() { return changesJson; }
    public String getPreviousData() { return previousData; }
    public String getNewData() { return newData; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }

    /** The only way to create a row. There is deliberately no way to change one. */
    public static final class Builder {
        private Long tenantId, actorTenantId, actorUserId, entityId;
        private String actorName, actorRole, module, entityType, entityLabel;
        private String changesJson, previousData, newData, ipAddress, userAgent;
        private AuditAction action;

        public Builder tenantId(Long v) { this.tenantId = v; return this; }
        public Builder actorTenantId(Long v) { this.actorTenantId = v; return this; }
        public Builder actorUserId(Long v) { this.actorUserId = v; return this; }
        public Builder actorName(String v) { this.actorName = v; return this; }
        public Builder actorRole(String v) { this.actorRole = v; return this; }
        public Builder module(String v) { this.module = v; return this; }
        public Builder entityType(String v) { this.entityType = v; return this; }
        public Builder entityId(Long v) { this.entityId = v; return this; }
        public Builder entityLabel(String v) { this.entityLabel = v; return this; }
        public Builder action(AuditAction v) { this.action = v; return this; }
        public Builder changesJson(String v) { this.changesJson = v; return this; }
        public Builder previousData(String v) { this.previousData = v; return this; }
        public Builder newData(String v) { this.newData = v; return this; }
        public Builder ipAddress(String v) { this.ipAddress = v; return this; }
        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public AuditLog build() { return new AuditLog(this); }
    }
}