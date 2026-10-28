package com.solar.Quotation.entity;


import com.solar.Quotation.Enum.ReferralPaymentMode;
import com.solar.Quotation.Enum.ReferralStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One payable referral. It is created from a quotation that was referred by a channel partner.
 * channelPartnerId (TenantBaseEntity) = the partner that OWNS the quotation.
 * referrerPartnerId                   = the partner that referred the customer and gets the commission.
 * Amounts are snapshots taken from the quotation, so later edits in Settings never change a payout.
 * There is no unique index on quotation_id on purpose: a removed referral is soft deleted and the same
 * quotation can get a new one. The service guarantees one live row per quotation.
 */
@Entity
@Table(name = "referral",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_partner_id", "code"}),
        indexes = {
                @Index(name = "idx_referral_quotation", columnList = "quotation_id"),
                @Index(name = "idx_referral_owner_date", columnList = "channel_partner_id,quote_date"),
                @Index(name = "idx_referral_referrer_status", columnList = "referrer_partner_id,status")
        })
public class Referral extends TenantBaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(name = "quotation_id", nullable = false)
    private Long quotationId;

    @Column(name = "quotation_code", nullable = false, length = 30)
    private String quotationCode;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "referrer_partner_id", nullable = false)
    private Long referrerPartnerId;

    @Column(name = "referrer_name", nullable = false, length = 200)
    private String referrerName;

    @Column(name = "referrer_code", length = 40)
    private String referrerCode;

    @Column(name = "quotation_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal quotationAmount;

    @Column(name = "referral_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal referralPercent;

    @Column(name = "referral_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal referralAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferralStatus status = ReferralStatus.PENDING;

    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    /** Day the quotation was accepted (referral became payable). */
    @Column(name = "accepted_on")
    private LocalDate acceptedOn;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "paid_by")
    private Long paidBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 20)
    private ReferralPaymentMode paymentMode;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(length = 300)
    private String remarks;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getQuotationId() { return quotationId; }
    public void setQuotationId(Long quotationId) { this.quotationId = quotationId; }
    public String getQuotationCode() { return quotationCode; }
    public void setQuotationCode(String quotationCode) { this.quotationCode = quotationCode; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getReferrerPartnerId() { return referrerPartnerId; }
    public void setReferrerPartnerId(Long referrerPartnerId) { this.referrerPartnerId = referrerPartnerId; }
    public String getReferrerName() { return referrerName; }
    public void setReferrerName(String referrerName) { this.referrerName = referrerName; }
    public String getReferrerCode() { return referrerCode; }
    public void setReferrerCode(String referrerCode) { this.referrerCode = referrerCode; }
    public BigDecimal getQuotationAmount() { return quotationAmount; }
    public void setQuotationAmount(BigDecimal quotationAmount) { this.quotationAmount = quotationAmount; }
    public BigDecimal getReferralPercent() { return referralPercent; }
    public void setReferralPercent(BigDecimal referralPercent) { this.referralPercent = referralPercent; }
    public BigDecimal getReferralAmount() { return referralAmount; }
    public void setReferralAmount(BigDecimal referralAmount) { this.referralAmount = referralAmount; }
    public ReferralStatus getStatus() { return status; }
    public void setStatus(ReferralStatus status) { this.status = status; }
    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }
    public LocalDate getAcceptedOn() { return acceptedOn; }
    public void setAcceptedOn(LocalDate acceptedOn) { this.acceptedOn = acceptedOn; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public Long getPaidBy() { return paidBy; }
    public void setPaidBy(Long paidBy) { this.paidBy = paidBy; }
    public ReferralPaymentMode getPaymentMode() { return paymentMode; }
    public void setPaymentMode(ReferralPaymentMode paymentMode) { this.paymentMode = paymentMode; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
