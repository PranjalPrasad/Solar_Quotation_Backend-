package com.solar.Quotation.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One counter per (partner, prefix). Used for CUST-1001, PRJ-1001, USR-1001, CP-0001 ... */
@Entity
@Table(name = "code_sequence",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_partner_id", "prefix"}))
public class CodeSequence extends TenantBaseEntity {

    @Column(nullable = false, length = 20)
    private String prefix;

    @Column(name = "last_no", nullable = false)
    private long lastNo;

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public long getLastNo() { return lastNo; }
    public void setLastNo(long lastNo) { this.lastNo = lastNo; }
}