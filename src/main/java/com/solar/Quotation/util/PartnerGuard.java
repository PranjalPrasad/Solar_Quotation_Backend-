package com.solar.Quotation.util;

import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import org.springframework.stereotype.Component;

@Component
public class PartnerGuard {

    private final ChannelPartnerRepository repository;

    public PartnerGuard(ChannelPartnerRepository repository) {
        this.repository = repository;
    }

    public void assertUsable(Long partnerId) {
        ChannelPartner p = repository.findByIdAndDeletedFalse(partnerId)
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Channel partner not found"));
        if (p.getPartnerType() != PartnerType.PARTNER) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Select a channel partner. The platform itself cannot own business records");
        }
        if (p.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessException("BUSINESS_RULE", "Channel partner is inactive");
        }
    }
}
