package com.solar.Quotation.dto.requestdto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Create, update and preview. There is NO partnerId: the quotation belongs to the partner of its customer,
 * so Super Admin just picks a customer. There are NO totals either: the server calculates every amount.
 */
public class QuotationRequestDto {

    @NotNull(message = "Customer is required")
    private Long customerId;

    /** Optional. When sent, the project must belong to the same customer. */
    private Long projectId;

    @NotNull(message = "System size is required")
    @DecimalMin(value = "0.1", message = "System size must be at least 0.1 kW")
    @DecimalMax(value = "100000", message = "System size is too large")
    private BigDecimal systemSizeKw;

    @Size(max = 50, message = "Mounting is too long")
    private String mounting;

    /** Empty means "today + validityDays from Settings". */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    @NotEmpty(message = "Add at least one item")
    @Size(max = 100, message = "Too many items")
    @Valid
    private List<QuotationItemRequestDto> items;

    @DecimalMin(value = "0", message = "Installation cost cannot be negative")
    private BigDecimal installationCost;

    @DecimalMin(value = "0", message = "Transport cost cannot be negative")
    private BigDecimal transportCost;

    @Size(max = 80, message = "Label is too long")
    private String otherLabel;

    @DecimalMin(value = "0", message = "Other cost cannot be negative")
    private BigDecimal otherCost;

    @NotNull(message = "GST percent is required")
    @DecimalMin(value = "0", message = "GST cannot be negative")
    @DecimalMax(value = "100", message = "GST cannot be more than 100")
    private BigDecimal gstPercent;

    @NotBlank(message = "Discount type is required")
    @Pattern(regexp = "PERCENT|FLAT", message = "Discount type must be PERCENT or FLAT")
    private String discountType;

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discountValue;

    @DecimalMin(value = "0", message = "Tariff cannot be negative")
    private BigDecimal tariff;

    @DecimalMin(value = "0", message = "Escalation cannot be negative")
    @DecimalMax(value = "100", message = "Escalation cannot be more than 100")
    private BigDecimal escalationPercent;

    @DecimalMin(value = "0", message = "Advance cannot be negative")
    private BigDecimal advance;

    @Size(max = 4000, message = "Terms are too long")
    private String terms;

    /** Empty means the defaults from Settings. When sent, the four values must add up to 100. */
    @Valid
    private PaymentTermsRequest paymentTerms;

    /** Empty means the bank details from Settings. */
    @Valid
    private BankRequest bank;

    @Valid
    private ReferralRequest referral;

    public static class PaymentTermsRequest {
        @NotNull(message = "Advance % is required")
        @DecimalMin(value = "0", message = "Advance % cannot be negative")
        @DecimalMax(value = "100", message = "Advance % cannot be more than 100")
        private BigDecimal advance;

        @NotNull(message = "Material % is required")
        @DecimalMin(value = "0", message = "Material % cannot be negative")
        @DecimalMax(value = "100", message = "Material % cannot be more than 100")
        private BigDecimal material;

        @NotNull(message = "Installation % is required")
        @DecimalMin(value = "0", message = "Installation % cannot be negative")
        @DecimalMax(value = "100", message = "Installation % cannot be more than 100")
        private BigDecimal installation;

        @NotNull(message = "Balance % is required")
        @DecimalMin(value = "0", message = "Balance % cannot be negative")
        @DecimalMax(value = "100", message = "Balance % cannot be more than 100")
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

    public static class BankRequest {
        @Size(max = 150, message = "Account name is too long")
        private String accountName;
        @Size(max = 100, message = "Bank name is too long")
        private String bankName;
        @Size(max = 40, message = "Account number is too long")
        private String accountNumber;
        @Size(max = 20, message = "IFSC is too long")
        private String ifsc;
        @Size(max = 100, message = "Branch is too long")
        private String branch;

        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }
        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        public String getIfsc() { return ifsc; }
        public void setIfsc(String ifsc) { this.ifsc = ifsc; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
    }

    public static class ReferralRequest {
        private boolean referred;

        @Size(max = 150, message = "Referrer name is too long")
        private String referredBy;

        @Pattern(regexp = "|CUSTOMER|CHANNEL_PARTNER|OTHER", message = "Referrer type must be CUSTOMER, CHANNEL_PARTNER or OTHER")
        private String referrerType;

        /** Optional. When empty the server generates one. */
        @Size(max = 30, message = "Referral code is too long")
        private String referralCode;

        @DecimalMin(value = "0", message = "Referral % cannot be negative")
        @DecimalMax(value = "100", message = "Referral % cannot be more than 100")
        private BigDecimal referralPercent;

        public boolean isReferred() { return referred; }
        public void setReferred(boolean referred) { this.referred = referred; }
        public String getReferredBy() { return referredBy; }
        public void setReferredBy(String referredBy) { this.referredBy = referredBy; }
        public String getReferrerType() { return referrerType; }
        public void setReferrerType(String referrerType) { this.referrerType = referrerType; }
        public String getReferralCode() { return referralCode; }
        public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
        public BigDecimal getReferralPercent() { return referralPercent; }
        public void setReferralPercent(BigDecimal referralPercent) { this.referralPercent = referralPercent; }
    }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public BigDecimal getSystemSizeKw() { return systemSizeKw; }
    public void setSystemSizeKw(BigDecimal systemSizeKw) { this.systemSizeKw = systemSizeKw; }
    public String getMounting() { return mounting; }
    public void setMounting(String mounting) { this.mounting = mounting; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public List<QuotationItemRequestDto> getItems() { return items; }
    public void setItems(List<QuotationItemRequestDto> items) { this.items = items; }
    public BigDecimal getInstallationCost() { return installationCost; }
    public void setInstallationCost(BigDecimal installationCost) { this.installationCost = installationCost; }
    public BigDecimal getTransportCost() { return transportCost; }
    public void setTransportCost(BigDecimal transportCost) { this.transportCost = transportCost; }
    public String getOtherLabel() { return otherLabel; }
    public void setOtherLabel(String otherLabel) { this.otherLabel = otherLabel; }
    public BigDecimal getOtherCost() { return otherCost; }
    public void setOtherCost(BigDecimal otherCost) { this.otherCost = otherCost; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getTariff() { return tariff; }
    public void setTariff(BigDecimal tariff) { this.tariff = tariff; }
    public BigDecimal getEscalationPercent() { return escalationPercent; }
    public void setEscalationPercent(BigDecimal escalationPercent) { this.escalationPercent = escalationPercent; }
    public BigDecimal getAdvance() { return advance; }
    public void setAdvance(BigDecimal advance) { this.advance = advance; }
    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public PaymentTermsRequest getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(PaymentTermsRequest paymentTerms) { this.paymentTerms = paymentTerms; }
    public BankRequest getBank() { return bank; }
    public void setBank(BankRequest bank) { this.bank = bank; }
    public ReferralRequest getReferral() { return referral; }
    public void setReferral(ReferralRequest referral) { this.referral = referral; }
}
