package com.solar.Quotation.service;

import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The id of the built-in PLATFORM tenant (the channel_partner row Super Admin users belong to).
 * Looked up once and cached: the row is seeded by data.sql and never changes id.
 */
@Component
public class PlatformTenant {

    private final ChannelPartnerRepository partners;
    private volatile Long cachedId;

    public PlatformTenant(ChannelPartnerRepository partners) {
        this.partners = partners;
    }

    /** @throws IllegalStateException when the PLATFORM partner is missing (data.sql did not run). */
    public Long id() {
        Long id = cachedId;
        if (id != null) return id;
        List<Long> ids = partners.findIdsByType(PartnerType.PLATFORM, PageRequest.of(0, 1));
        if (ids.isEmpty()) {
            throw new IllegalStateException("PLATFORM partner not found (partner_type = PLATFORM)");
        }
        cachedId = ids.get(0);
        return cachedId;
    }
}
