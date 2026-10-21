package com.solar.Quotation.dto.requestdto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** The four payment-term percentages must add up to exactly 100 (checked in the service). */
public class QuotationDefaultsRequestDto {

    @NotBlank(message = "Terms are required")
    @Size(max = 4000, message = "Terms are too long")
    private String terms;

    @NotNull(message = "Validity days is required")
    @Min(value = 1, message = "Validity must be at least 1 day")
    @Max(value = 365, message = "Validity cannot exceed 365 days")
    private Integer validityDays;

    @NotBlank(message = "Quotation prefix is required")
    @Pattern(regexp = "^[A-Z0-9]{2,6}$", message = "Prefix must be 2 to 6 capital letters or digits")
    private String prefix;

    @Valid
    @NotNull(message = "Payment terms are required")
    private PaymentTerms paymentTerms;

    @DecimalMin(value = "0", message = "Referral percent cannot be negative")
    @DecimalMax(value = "100", message = "Referral percent cannot exceed 100")
    private BigDecimal customerReferralPercent;

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public Integer getValidityDays() { return validityDays; }
    public void setValidityDays(Integer validityDays) { this.validityDays = validityDays; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public PaymentTerms getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(PaymentTerms paymentTerms) { this.paymentTerms = paymentTerms; }
    public BigDecimal getCustomerReferralPercent() { return customerReferralPercent; }
    public void setCustomerReferralPercent(BigDecimal customerReferralPercent) { this.customerReferralPercent = customerReferralPercent; }

    public static class PaymentTerms {

        @NotNull(message = "Advance percent is required")
        @DecimalMin(value = "0", message = "Cannot be negative")
        @DecimalMax(value = "100", message = "Cannot exceed 100")
        private BigDecimal advance;

        @NotNull(message = "Material percent is required")
        @DecimalMin(value = "0", message = "Cannot be negative")
        @DecimalMax(value = "100", message = "Cannot exceed 100")
        private BigDecimal material;

        @NotNull(message = "Installation percent is required")
        @DecimalMin(value = "0", message = "Cannot be negative")
        @DecimalMax(value = "100", message = "Cannot exceed 100")
        private BigDecimal installation;

        @NotNull(message = "Balance percent is required")
        @DecimalMin(value = "0", message = "Cannot be negative")
        @DecimalMax(value = "100", message = "Cannot exceed 100")
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
