package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One rule for everybody: every user, Super Admin included, OWNS data under their own partner id
 * (Super Admin's own partner is the PLATFORM tenant).
 *
 *   WRITES  (create / update / delete / status change, and any non-GET):  effectivePartnerId(..), requirePartnerIdForCreate(..)
 *           -> always the caller's own tenant. A partner id sent by the client is ignored (and logged at WARN).
 *   READS   (GET only):  effectiveReadPartnerId(..)
 *           -> own tenant, except data scope ALL (Super Admin) may look at ONE other partner through viewPartnerId.
 *
 * Logging: decisions at DEBUG, cross-tenant attempts at WARN with userId, requested id and own partnerId.
 */
@Component
public class TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(TenantResolver.class);

    /** The caller's own tenant. Super Admin: the PLATFORM partner id. */
    public Long ownPartnerId() {
        return own(required());
    }

    /** For writes. Same as effectivePartnerId(null). */
    public Long effectivePartnerId() {
        return effectivePartnerId(null);
    }

    /**
     * For writes and any non-GET code path. Returns the caller's own tenant for EVERY role (never null).
     * requestedPartnerId is kept only so old call sites still compile; it can never widen access.
     */
    public Long effectivePartnerId(Long requestedPartnerId) {
        return resolveOwn("effectivePartnerId", requestedPartnerId);
    }

    /** For create. Ignores any partnerId from the client; the record is owned by the caller's own tenant. */
    public Long requirePartnerIdForCreate(Long requestedPartnerId) {
        return resolveOwn("create", requestedPartnerId);
    }

    /** GET endpoints: uses the ?viewPartnerId= value that JwtAuthFilter put on the request. */
    public Long effectiveReadPartnerId() {
        return effectiveReadPartnerId(required().getViewPartnerId());
    }

    /**
     * GET endpoints only. Honoured for data scope ALL on a read request; for every other caller (and for any
     * non-GET request) the view is ignored and the caller's own tenant is returned. Never null.
     */
    public Long effectiveReadPartnerId(Long viewPartnerId) {
        CurrentUser u = required();
        Long own = own(u);
        if (viewPartnerId == null || viewPartnerId.equals(own)) {
            log.debug("[TENANT] read userId={} scope={} -> own partnerId={}", u.getUserId(), u.getDataScope(), own);
            return own;
        }
        if (!u.isReadRequest()) {
            log.warn("[TENANT] view partner on a non-read request ignored userId={} requestedPartnerId={} ownPartnerId={}",
                    u.getUserId(), viewPartnerId, own);
            return own;
        }
        if (u.getDataScope() == DataScope.ALL) {
            log.debug("[TENANT] read userId={} scope=ALL viewing partnerId={} (own={})", u.getUserId(), viewPartnerId, own);
            return viewPartnerId;
        }
        log.warn("[TENANT] cross-tenant read blocked, own data returned userId={} requestedPartnerId={} ownPartnerId={}",
                u.getUserId(), viewPartnerId, own);
        return own;
    }

    /**
     * Use after loading a record WITHOUT a partner filter, before changing it. A record of another tenant
     * looks exactly like a missing record (404) and is logged at WARN.
     */
    public void assertWritable(Long recordPartnerId, String entityName, Object id) {
        CurrentUser u = required();
        Long own = own(u);
        if (recordPartnerId == null || !recordPartnerId.equals(own)) {
            log.warn("[TENANT] cross-tenant write blocked userId={} entity={} id={} recordPartnerId={} ownPartnerId={}",
                    u.getUserId(), entityName, id, recordPartnerId, own);
            throw new ResourceNotFoundException(entityName + " not found");
        }
    }

    /** For data scope OWN: restrict to records created by this user. Null means no extra restriction. */
    public Long ownerFilter() {
        CurrentUser u = required();
        return u.getDataScope() == DataScope.OWN ? u.getUserId() : null;
    }

    private Long resolveOwn(String op, Long requested) {
        CurrentUser u = required();
        Long own = own(u);
        if (requested != null && !requested.equals(own)) {
            log.warn("[TENANT] client partnerId ignored on {} userId={} requestedPartnerId={} ownPartnerId={}",
                    op, u.getUserId(), requested, own);
        } else {
            log.debug("[TENANT] {} userId={} scope={} -> own partnerId={}", op, u.getUserId(), u.getDataScope(), own);
        }
        return own;
    }

    private Long own(CurrentUser u) {
        if (u.getPartnerId() == null) {
            log.warn("[TENANT] user has no partner userId={}", u.getUserId());
            throw new ForbiddenException("FORBIDDEN", "User has no channel partner");
        }
        return u.getPartnerId();
    }

    private CurrentUser required() {
        CurrentUser u = TenantContext.get();
        if (u == null) throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        return u;
    }
}