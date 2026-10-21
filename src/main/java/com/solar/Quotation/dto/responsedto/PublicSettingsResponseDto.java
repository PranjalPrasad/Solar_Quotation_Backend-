package com.solar.Quotation.dto.responsedto;

import java.util.List;

public class PublicSettingsResponseDto {

    private BrandingResponseDto branding;
    private BankDetailsResponseDto bank;
    private QuotationDefaultsResponseDto quotationDefaults;
    private SavingsConfigResponseDto savingsConfig;
    private List<SubsidyRuleResponseDto> subsidyRules;
    private List<GstRuleResponseDto> gstRules;

    public BrandingResponseDto getBranding() { return branding; }
    public void setBranding(BrandingResponseDto branding) { this.branding = branding; }
    public BankDetailsResponseDto getBank() { return bank; }
    public void setBank(BankDetailsResponseDto bank) { this.bank = bank; }
    public QuotationDefaultsResponseDto getQuotationDefaults() { return quotationDefaults; }
    public void setQuotationDefaults(QuotationDefaultsResponseDto quotationDefaults) { this.quotationDefaults = quotationDefaults; }
    public SavingsConfigResponseDto getSavingsConfig() { return savingsConfig; }
    public void setSavingsConfig(SavingsConfigResponseDto savingsConfig) { this.savingsConfig = savingsConfig; }
    public List<SubsidyRuleResponseDto> getSubsidyRules() { return subsidyRules; }
    public void setSubsidyRules(List<SubsidyRuleResponseDto> subsidyRules) { this.subsidyRules = subsidyRules; }
    public List<GstRuleResponseDto> getGstRules() { return gstRules; }
    public void setGstRules(List<GstRuleResponseDto> gstRules) { this.gstRules = gstRules; }
}
