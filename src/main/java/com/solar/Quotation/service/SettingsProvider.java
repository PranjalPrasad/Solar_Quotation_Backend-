package com.solar.Quotation.service;

import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;

import java.util.List;

/**
 * Settings are PER PARTNER. The no-argument methods use the logged-in user's own partner (and the PLATFORM
 * partner when nobody is logged in, for example the password-reset e-mail). The overloads take an explicit
 * partner id: pass the partner that owns the record being built (the quotation's partner), so a Super Admin
 * creating a quotation for partner X uses X's settings and never the platform's.
 */
public interface SettingsProvider {

    BrandingResponseDto branding(Long partnerId);
    BankDetailsResponseDto bank(Long partnerId);
    QuotationDefaultsResponseDto quotationDefaults(Long partnerId);
    SavingsConfigResponseDto savingsConfig(Long partnerId);
    List<SubsidyRuleResponseDto> activeSubsidyRules(Long partnerId);
    List<GstRuleResponseDto> gstRules(Long partnerId);

    BrandingResponseDto branding();

    BankDetailsResponseDto bank();

    QuotationDefaultsResponseDto quotationDefaults();

    SavingsConfigResponseDto savingsConfig();

    /** Only active rules, in display order. Feed them to SubsidyCalculator. */
    List<SubsidyRuleResponseDto> activeSubsidyRules();

    List<GstRuleResponseDto> gstRules();
}