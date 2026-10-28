package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class QuotationResponseDto {

    private Long id;
    private String code;
    private String status;
    private LocalDate quoteDate;
    private LocalDate validUntil;
    private CustomerBlock customer;
    private Long projectId;
    private BigDecimal systemSizeKw;
    private String mounting;
    private List<QuotationItemResponseDto> items;

    private BigDecimal itemsTotal;
    private BigDecimal installationCost;
    private BigDecimal transportCost;
    private String otherLabel;
    private BigDecimal otherCost;
    private BigDecimal subtotal;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal gstPercent;
    private BigDecimal taxableAmount;
    private BigDecimal sgstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal totalAmount;
    private String amountInWords;
    private BigDecimal advance;
    private BigDecimal balanceAmount;
    private BigDecimal subsidyAmount;
    private BigDecimal netInvestment;

    private BigDecimal tariff;
    private BigDecimal escalationPercent;
    private Savings savings;

    private String terms;
    private PaymentTerms paymentTerms;
    private Bank bank;
    private Referral referral;
    private PartnerRefResponseDto partner;
    private AuditInfoResponseDto audit;

    /** Live view of the customer record. */
    public static class CustomerBlock {
        private Long id;
        private String code;
        private String name;
        private String mobile;
        private String email;
        private String address;
        private String city;
        private String state;
        private String pincode;
        private String gstNo;
        private String type;
        private String consumerNo;
        private String consumerCategory;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getPincode() { return pincode; }
        public void setPincode(String pincode) { this.pincode = pincode; }
        public String getGstNo() { return gstNo; }
        public void setGstNo(String gstNo) { this.gstNo = gstNo; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getConsumerNo() { return consumerNo; }
        public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
        public String getConsumerCategory() { return consumerCategory; }
        public void setConsumerCategory(String consumerCategory) { this.consumerCategory = consumerCategory; }
    }

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

    public static class Bank {
        private String accountName;
        private String bankName;
        private String accountNumber;
        private String ifsc;
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

    public static class Referral {
        private boolean referred;
        private String referredBy;
        private String referrerType;
        private String referralCode;
        private BigDecimal referralPercent;
        private BigDecimal referralAmount;
        private String referralStatus;
        private Long referrerPartnerId;

        public Long getReferrerPartnerId() { return referrerPartnerId; }
        public void setReferrerPartnerId(Long referrerPartnerId) { this.referrerPartnerId = referrerPartnerId; }
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
        public BigDecimal getReferralAmount() { return referralAmount; }
        public void setReferralAmount(BigDecimal referralAmount) { this.referralAmount = referralAmount; }
        public String getReferralStatus() { return referralStatus; }
        public void setReferralStatus(String referralStatus) { this.referralStatus = referralStatus; }
    }

    /** Computed on every read from the savings settings and the quotation's tariff / escalation. Not stored. */
    public static class Savings {
        private BigDecimal annualUnits;
        private BigDecimal annualSavings;
        private BigDecimal monthlySavings;
        private BigDecimal savings25yr;
        private BigDecimal carbonPerYearKg;
        private BigDecimal carbon25yrTonnes;
        private BigDecimal roiPercent;
        private BigDecimal paybackYears;
        private int projectionYears;

        public BigDecimal getAnnualUnits() { return annualUnits; }
        public void setAnnualUnits(BigDecimal annualUnits) { this.annualUnits = annualUnits; }
        public BigDecimal getAnnualSavings() { return annualSavings; }
        public void setAnnualSavings(BigDecimal annualSavings) { this.annualSavings = annualSavings; }
        public BigDecimal getMonthlySavings() { return monthlySavings; }
        public void setMonthlySavings(BigDecimal monthlySavings) { this.monthlySavings = monthlySavings; }
        public BigDecimal getSavings25yr() { return savings25yr; }
        public void setSavings25yr(BigDecimal savings25yr) { this.savings25yr = savings25yr; }
        public BigDecimal getCarbonPerYearKg() { return carbonPerYearKg; }
        public void setCarbonPerYearKg(BigDecimal carbonPerYearKg) { this.carbonPerYearKg = carbonPerYearKg; }
        public BigDecimal getCarbon25yrTonnes() { return carbon25yrTonnes; }
        public void setCarbon25yrTonnes(BigDecimal carbon25yrTonnes) { this.carbon25yrTonnes = carbon25yrTonnes; }
        public BigDecimal getRoiPercent() { return roiPercent; }
        public void setRoiPercent(BigDecimal roiPercent) { this.roiPercent = roiPercent; }
        public BigDecimal getPaybackYears() { return paybackYears; }
        public void setPaybackYears(BigDecimal paybackYears) { this.paybackYears = paybackYears; }
        public int getProjectionYears() { return projectionYears; }
        public void setProjectionYears(int projectionYears) { this.projectionYears = projectionYears; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public CustomerBlock getCustomer() { return customer; }
    public void setCustomer(CustomerBlock customer) { this.customer = customer; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public BigDecimal getSystemSizeKw() { return systemSizeKw; }
    public void setSystemSizeKw(BigDecimal systemSizeKw) { this.systemSizeKw = systemSizeKw; }
    public String getMounting() { return mounting; }
    public void setMounting(String mounting) { this.mounting = mounting; }
    public List<QuotationItemResponseDto> getItems() { return items; }
    public void setItems(List<QuotationItemResponseDto> items) { this.items = items; }
    public BigDecimal getItemsTotal() { return itemsTotal; }
    public void setItemsTotal(BigDecimal itemsTotal) { this.itemsTotal = itemsTotal; }
    public BigDecimal getInstallationCost() { return installationCost; }
    public void setInstallationCost(BigDecimal installationCost) { this.installationCost = installationCost; }
    public BigDecimal getTransportCost() { return transportCost; }
    public void setTransportCost(BigDecimal transportCost) { this.transportCost = transportCost; }
    public String getOtherLabel() { return otherLabel; }
    public void setOtherLabel(String otherLabel) { this.otherLabel = otherLabel; }
    public BigDecimal getOtherCost() { return otherCost; }
    public void setOtherCost(BigDecimal otherCost) { this.otherCost = otherCost; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(BigDecimal sgstAmount) { this.sgstAmount = sgstAmount; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(BigDecimal cgstAmount) { this.cgstAmount = cgstAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getAmountInWords() { return amountInWords; }
    public void setAmountInWords(String amountInWords) { this.amountInWords = amountInWords; }
    public BigDecimal getAdvance() { return advance; }
    public void setAdvance(BigDecimal advance) { this.advance = advance; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }
    public BigDecimal getSubsidyAmount() { return subsidyAmount; }
    public void setSubsidyAmount(BigDecimal subsidyAmount) { this.subsidyAmount = subsidyAmount; }
    public BigDecimal getNetInvestment() { return netInvestment; }
    public void setNetInvestment(BigDecimal netInvestment) { this.netInvestment = netInvestment; }
    public BigDecimal getTariff() { return tariff; }
    public void setTariff(BigDecimal tariff) { this.tariff = tariff; }
    public BigDecimal getEscalationPercent() { return escalationPercent; }
    public void setEscalationPercent(BigDecimal escalationPercent) { this.escalationPercent = escalationPercent; }
    public Savings getSavings() { return savings; }
    public void setSavings(Savings savings) { this.savings = savings; }
    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public PaymentTerms getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(PaymentTerms paymentTerms) { this.paymentTerms = paymentTerms; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
    public Referral getReferral() { return referral; }
    public void setReferral(Referral referral) { this.referral = referral; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public AuditInfoResponseDto getAudit() { return audit; }
    public void setAudit(AuditInfoResponseDto audit) { this.audit = audit; }
}