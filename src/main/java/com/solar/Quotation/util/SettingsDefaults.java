package com.solar.Quotation.util;

import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;

import java.math.BigDecimal;

public final class SettingsDefaults {

    private SettingsDefaults() { }

    public static BrandingResponseDto branding() {
        BrandingResponseDto d = new BrandingResponseDto();
        d.setName("Solar Quotation Management Pvt. Ltd.");
        d.setAddress("4th Floor, Highland Park, Pune, Maharashtra 411001");
        d.setPhone("+91 98765 43210");
        d.setEmail("info@sqms.example");
        d.setWebsite("www.sqms.example");
        d.setLogoPath("");
        return d;
    }

    public static BankDetailsResponseDto bank() {
        BankDetailsResponseDto d = new BankDetailsResponseDto();
        d.setAccountName("Solar Quotation Management Pvt. Ltd.");
        d.setBankName("HDFC Bank");
        d.setAccountNumber("50100234567890");
        d.setIfsc("HDFC0001234");
        d.setBranch("Highland Park, Pune");
        return d;
    }

    public static QuotationDefaultsResponseDto quotationDefaults() {
        QuotationDefaultsResponseDto d = new QuotationDefaultsResponseDto();
        d.setTerms("1. Prices are inclusive of installation & commissioning.\n"
                + "2. 50% advance required to confirm the order.\n"
                + "3. Subsidy amount subject to government approval & disbursal timelines.\n"
                + "4. Warranty as per manufacturer terms.");
        d.setValidityDays(15);
        d.setPrefix("SQ");
        QuotationDefaultsResponseDto.PaymentTerms p = new QuotationDefaultsResponseDto.PaymentTerms();
        p.setAdvance(new BigDecimal("50"));
        p.setMaterial(new BigDecimal("25"));
        p.setInstallation(new BigDecimal("15"));
        p.setBalance(new BigDecimal("10"));
        d.setPaymentTerms(p);
        d.setCustomerReferralPercent(new BigDecimal("5"));
        return d;
    }

    public static SavingsConfigResponseDto savingsConfig() {
        SavingsConfigResponseDto d = new SavingsConfigResponseDto();
        d.setUnitsPerKwPerDay(new BigDecimal("4"));
        d.setDegradationPercent(new BigDecimal("0.5"));
        d.setCo2FactorKgPerKwh(new BigDecimal("0.82"));
        d.setProjectionYears(25);
        d.setMarketRatePerKw(new BigDecimal("58000"));
        return d;
    }
}
