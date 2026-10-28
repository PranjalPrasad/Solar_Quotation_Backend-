package com.solar.Quotation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** One saved change of a quotation. Rows are append-only: never updated, never deleted. */
@Entity
@Table(name = "quotation_history")
public class QuotationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quotation_id", nullable = false)
    private Long quotationId;

    @Column(name = "quotation_code", nullable = false, length = 50)
    private String quotationCode;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 500)
    private String summary;

    @Column(length = 20)
    private String status;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "changed_by_id")
    private Long changedById;

    @Column(name = "changed_by_name", length = 150)
    private String changedByName;

    @Column(name = "changed_by_role", length = 50)
    private String changedByRole;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Lob
    @Column(name = "changes_json")
    private String changesJson;

    @Lob
    @Column(name = "snapshot_json")
    private String snapshotJson;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public Long getQuotationId() { return quotationId; }
    public void setQuotationId(Long quotationId) { this.quotationId = quotationId; }
    public String getQuotationCode() { return quotationCode; }
    public void setQuotationCode(String quotationCode) { this.quotationCode = quotationCode; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Long getChangedById() { return changedById; }
    public void setChangedById(Long changedById) { this.changedById = changedById; }
    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }
    public String getChangedByRole() { return changedByRole; }
    public void setChangedByRole(String changedByRole) { this.changedByRole = changedByRole; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public String getChangesJson() { return changesJson; }
    public void setChangesJson(String changesJson) { this.changesJson = changesJson; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
}
