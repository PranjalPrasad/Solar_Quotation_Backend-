package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver {

    /** For list and get. Partner roles always get their own partner; the requested value is ignored. */
    public Long effectivePartnerId(Long requestedPartnerId) {
        CurrentUser u = required();
        if (u.getDataScope() == DataScope.ALL) {
            return requestedPartnerId;          // null means every partner (Super Admin only)
        }
        return u.getPartnerId();
    }

    /** For create. Super Admin must say which partner owns the record. */
    public Long requirePartnerIdForCreate(Long requestedPartnerId) {
        CurrentUser u = required();
        if (u.getDataScope() == DataScope.ALL) {
            if (requestedPartnerId == null) throw new BusinessException("partnerId is required");
            return requestedPartnerId;
        }
        return u.getPartnerId();
    }

    /** For data scope OWN: restrict to records created by this user. Null means no extra restriction. */
    public Long ownerFilter() {
        CurrentUser u = required();
        return u.getDataScope() == DataScope.OWN ? u.getUserId() : null;
    }

    private CurrentUser required() {
        CurrentUser u = TenantContext.get();
        if (u == null) throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        return u;
    }
}
