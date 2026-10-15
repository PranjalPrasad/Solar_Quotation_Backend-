package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class TenantBaseEntity extends BaseEntity {

    @Column(name = "channel_partner_id", nullable = false, updatable = false)
    private Long channelPartnerId;

    @PrePersist
    void checkTenant() {
        CurrentUser u = TenantContext.get();
        if (u != null && u.getDataScope() != DataScope.ALL
                && !u.getPartnerId().equals(channelPartnerId)) {
            throw new ForbiddenException("Cross-partner write blocked");
        }
    }

    public Long getChannelPartnerId() { return channelPartnerId; }
    public void setChannelPartnerId(Long channelPartnerId) { this.channelPartnerId = channelPartnerId; }
}
