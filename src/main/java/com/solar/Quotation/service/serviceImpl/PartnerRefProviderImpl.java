package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.service.PartnerRefProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Needs only the repository, so AuditServiceImpl and every other service can use it without a cycle. */
@Component
public class PartnerRefProviderImpl implements PartnerRefProvider {

    private final ChannelPartnerRepository repository;

    public PartnerRefProviderImpl(ChannelPartnerRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, PartnerRefResponseDto> refs(Collection<Long> partnerIds) {
        Map<Long, PartnerRefResponseDto> out = new HashMap<>();
        if (partnerIds == null || partnerIds.isEmpty()) {
            return out;
        }
        for (ChannelPartner p : repository.findAllById(partnerIds)) {
            out.put(p.getId(), new PartnerRefResponseDto(p.getId(), p.getCode(), p.getName()));
        }
        return out;
    }
}
