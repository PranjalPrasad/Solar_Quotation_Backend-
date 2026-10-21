package com.solar.Quotation.util;

import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;
import com.solar.Quotation.entity.GstRule;
import com.solar.Quotation.entity.SubsidyRule;

/** Entity to ResponseDto for the rule tables (shared by the settings service and the settings provider). */
public final class SettingsMapper {

    private SettingsMapper() { }

    public static SubsidyRuleResponseDto toDto(SubsidyRule r) {
        SubsidyRuleResponseDto d = new SubsidyRuleResponseDto();
        d.setId(r.getId());
        d.setType(r.getType());
        d.setCategory(r.getCategory());
        d.setValueType(r.getValueType().name());
        d.setValue(r.getValue());
        d.setCapMin(r.getCapMin());
        d.setCapMax(r.getCapMax());
        d.setMaxAmount(r.getMaxAmount());
        d.setActive(r.isActive());
        d.setSortOrder(r.getSortOrder());
        return d;
    }

    public static GstRuleResponseDto toDto(GstRule r) {
        GstRuleResponseDto d = new GstRuleResponseDto();
        d.setId(r.getId());
        d.setCategory(r.getCategory());
        d.setGstPercent(r.getGstPercent());
        d.setApplicableOn(r.getApplicableOn().name());
        return d;
    }
}
