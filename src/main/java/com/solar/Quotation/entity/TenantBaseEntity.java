package com.solar.Quotation.entity;

import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Last line of defence (L3): whatever a service forgets, a signed-in user - Super Admin included - can only
 * insert, update or delete rows of their OWN tenant. Code running with no signed-in user (jobs, seeding) is not checked.
 */
@MappedSuperclass
public abstract class TenantBaseEntity extends BaseEntity {

    private static final Logger log = LoggerFactory.getLogger(TenantBaseEntity.class);

    @Column(name = "channel_partner_id", nullable = false, updatable = false)
    private Long channelPartnerId;

    @PrePersist
    @PreUpdate
    @PreRemove
    void guardTenant() {
        CurrentUser u = TenantContext.get();
        if (u == null) return;
        if (u.isSuperAdmin() && platformMayWriteAcrossTenants()) return;
        if (u.getPartnerId() == null || !u.getPartnerId().equals(channelPartnerId)) {
            log.warn("[TENANT] cross-tenant write blocked userId={} entity={} entityPartnerId={} ownPartnerId={}",
                    u.getUserId(), getClass().getSimpleName(), channelPartnerId, u.getPartnerId());
            throw new ForbiddenException("TENANT_VIOLATION", "Cross-partner write blocked");
        }
    }

    /**
     * Business data (customer, project, product, quotation): false, so Super Admin cannot change partner data.
     * Override with true ONLY in entities that Super Admin must manage for partners (for example AppUser, when it
     * extends this class, so Super Admin can still create a new partner's first user).
     */
    protected boolean platformMayWriteAcrossTenants() { return false; }

    public Long getChannelPartnerId() { return channelPartnerId; }
    public void setChannelPartnerId(Long channelPartnerId) { this.channelPartnerId = channelPartnerId; }
}