package com.solar.Quotation.entity;
import com.solar.Quotation.Enum.FollowupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** channel_partner_id is copied from the customer, so follow-ups are filtered exactly like their parent. */
@Entity
@Table(name = "customer_followup",
        indexes = {
                @Index(name = "idx_followup_customer", columnList = "customer_id"),
                @Index(name = "idx_followup_partner_next", columnList = "channel_partner_id,next_date")
        })
public class CustomerFollowup extends TenantBaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "followup_date", nullable = false)
    private LocalDate followupDate;

    @Column(nullable = false, length = 500)
    private String remark;

    @Column(name = "next_date")
    private LocalDate nextDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowupStatus status = FollowupStatus.PENDING;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LocalDate getFollowupDate() { return followupDate; }
    public void setFollowupDate(LocalDate followupDate) { this.followupDate = followupDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public FollowupStatus getStatus() { return status; }
    public void setStatus(FollowupStatus status) { this.status = status; }
}
