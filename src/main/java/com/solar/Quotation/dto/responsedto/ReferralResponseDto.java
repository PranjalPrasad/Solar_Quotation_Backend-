package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReferralResponseDto {

    private Long id;
    private String code;
    private String status;
    private QuotationRef quotation;
    private CustomerRefResponseDto referredCustomer;
    private Referrer referrer;
    private BigDecimal quotationAmount;
    private BigDecimal referralPercent;
    private BigDecimal referralAmount;
    private LocalDate quoteDate;
    private LocalDate acceptedOn;
    private LocalDate paidOn;
    private String paidBy;
    private String paymentMode;
    private String paymentReference;
    private String remarks;
    private PartnerRefResponseDto partner;
    private AuditInfoResponseDto audit;

    public static class QuotationRef {
        private Long id;
        private String code;

        public QuotationRef() { }
        public QuotationRef(Long id, String code) { this.id = id; this.code = code; }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class Referrer {
        private String type;
        private Long partnerId;
        private String name;
        private String code;

        public Referrer() { }
        public Referrer(String type, Long partnerId, String name, String code) {
            this.type = type; this.partnerId = partnerId; this.name = name; this.code = code;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getPartnerId() { return partnerId; }
        public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public QuotationRef getQuotation() { return quotation; }
    public void setQuotation(QuotationRef quotation) { this.quotation = quotation; }
    public CustomerRefResponseDto getReferredCustomer() { return referredCustomer; }
    public void setReferredCustomer(CustomerRefResponseDto referredCustomer) { this.referredCustomer = referredCustomer; }
    public Referrer getReferrer() { return referrer; }
    public void setReferrer(Referrer referrer) { this.referrer = referrer; }
    public BigDecimal getQuotationAmount() { return quotationAmount; }
    public void setQuotationAmount(BigDecimal quotationAmount) { this.quotationAmount = quotationAmount; }
    public BigDecimal getReferralPercent() { return referralPercent; }
    public void setReferralPercent(BigDecimal referralPercent) { this.referralPercent = referralPercent; }
    public BigDecimal getReferralAmount() { return referralAmount; }
    public void setReferralAmount(BigDecimal referralAmount) { this.referralAmount = referralAmount; }
    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }
    public LocalDate getAcceptedOn() { return acceptedOn; }
    public void setAcceptedOn(LocalDate acceptedOn) { this.acceptedOn = acceptedOn; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public String getPaidBy() { return paidBy; }
    public void setPaidBy(String paidBy) { this.paidBy = paidBy; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public AuditInfoResponseDto getAudit() { return audit; }
    public void setAudit(AuditInfoResponseDto audit) { this.audit = audit; }
}
