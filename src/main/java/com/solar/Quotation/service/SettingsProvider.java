package com.solar.Quotation.service;

import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;

import java.util.List;

public interface SettingsProvider {

    BrandingResponseDto branding();

    BankDetailsResponseDto bank();

    QuotationDefaultsResponseDto quotationDefaults();

    SavingsConfigResponseDto savingsConfig();

    /** Only active rules, in display order. Feed them to SubsidyCalculator. */
    List<SubsidyRuleResponseDto> activeSubsidyRules();

    List<GstRuleResponseDto> gstRules();
}
