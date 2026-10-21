package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class QuotationDefaultsResponseDto {

    private String terms;
    private int validityDays;
    private String prefix;
    private PaymentTerms paymentTerms;
    private BigDecimal customerReferralPercent;

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public int getValidityDays() { return validityDays; }
    public void setValidityDays(int validityDays) { this.validityDays = validityDays; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public PaymentTerms getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(PaymentTerms paymentTerms) { this.paymentTerms = paymentTerms; }
    public BigDecimal getCustomerReferralPercent() { return customerReferralPercent; }
    public void setCustomerReferralPercent(BigDecimal customerReferralPercent) { this.customerReferralPercent = customerReferralPercent; }

    public static class PaymentTerms {
        private BigDecimal advance;
        private BigDecimal material;
        private BigDecimal installation;
        private BigDecimal balance;

        public BigDecimal getAdvance() { return advance; }
        public void setAdvance(BigDecimal advance) { this.advance = advance; }
        public BigDecimal getMaterial() { return material; }
        public void setMaterial(BigDecimal material) { this.material = material; }
        public BigDecimal getInstallation() { return installation; }
        public void setInstallation(BigDecimal installation) { this.installation = installation; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }
}
