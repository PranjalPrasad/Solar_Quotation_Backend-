package com.solar.Quotation.service;

import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Looks up {id, code, name} of channel partners. Every ResponseDto that carries a "partner" block uses this.
 * Implemented in the channelpartner module (Batch 2) by a small component that only needs the repository,
 * so audit and other services can use it without a circular dependency.
 */
public interface PartnerRefProvider {

    Map<Long, PartnerRefResponseDto> refs(Collection<Long> partnerIds);

    default PartnerRefResponseDto ref(Long partnerId) {
        if (partnerId == null) return null;
        return refs(List.of(partnerId)).get(partnerId);
    }
}
