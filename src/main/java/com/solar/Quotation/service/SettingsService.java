package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.BankDetailsRequestDto;
import com.solar.Quotation.dto.requestdto.BrandingRequestDto;
import com.solar.Quotation.dto.requestdto.GstRuleRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationDefaultsRequestDto;
import com.solar.Quotation.dto.requestdto.SavingsConfigRequestDto;
import com.solar.Quotation.dto.requestdto.SubsidyRuleRequestDto;
import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.PublicSettingsResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;

import java.util.List;

/** Settings screen operations (Super Admin only). Every save is audited. */
public interface SettingsService {

    BrandingResponseDto getBranding();
    BrandingResponseDto updateBranding(BrandingRequestDto req);

    BankDetailsResponseDto getBank();
    BankDetailsResponseDto updateBank(BankDetailsRequestDto req);

    QuotationDefaultsResponseDto getQuotationDefaults();
    QuotationDefaultsResponseDto updateQuotationDefaults(QuotationDefaultsRequestDto req);

    SavingsConfigResponseDto getSavingsConfig();
    SavingsConfigResponseDto updateSavingsConfig(SavingsConfigRequestDto req);

    List<SubsidyRuleResponseDto> subsidyRules();
    SubsidyRuleResponseDto createSubsidyRule(SubsidyRuleRequestDto req);
    SubsidyRuleResponseDto updateSubsidyRule(Long id, SubsidyRuleRequestDto req);
    void deleteSubsidyRule(Long id);

    List<GstRuleResponseDto> gstRules();
    GstRuleResponseDto createGstRule(GstRuleRequestDto req);
    GstRuleResponseDto updateGstRule(Long id, GstRuleRequestDto req);
    void deleteGstRule(Long id);

    /** For every logged-in role. */
    PublicSettingsResponseDto publicSettings();
}
