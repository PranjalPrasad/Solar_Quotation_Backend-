package com.solar.Quotation.util;

import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks that a partner may own a new record. A normal PARTNER is fine while ACTIVE.
 * The PLATFORM partner may own records ONLY when the caller is Super Admin and it is the caller's own partner.
 */
@Component
public class PartnerGuard {

    private static final Logger log = LoggerFactory.getLogger(PartnerGuard.class);

    private final ChannelPartnerRepository repository;

    public PartnerGuard(ChannelPartnerRepository repository) {
        this.repository = repository;
    }

    public void assertUsable(Long partnerId) {
        ChannelPartner p = repository.findByIdAndDeletedFalse(partnerId)
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Channel partner not found"));

        if (p.getPartnerType() == PartnerType.PLATFORM) {
            CurrentUser u = TenantContext.get();
            boolean allowed = u != null && u.isSuperAdmin() && partnerId.equals(u.getPartnerId());
            if (!allowed) {
                log.warn("[PARTNER] platform used as owner by a non-Super-Admin userId={} partnerId={}",
                        u == null ? null : u.getUserId(), partnerId);
                throw new BusinessException("VALIDATION_ERROR", "Only Super Admin can own records under the platform");
            }
            log.debug("[PARTNER] platform owner accepted partnerId={} userId={}", partnerId, u.getUserId());
        }
        if (p.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessException("BUSINESS_RULE", "Channel partner is inactive");
        }
    }
}