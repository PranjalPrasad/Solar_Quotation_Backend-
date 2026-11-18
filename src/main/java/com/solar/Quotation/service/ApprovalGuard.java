package com.solar.Quotation.service;

import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Rules for approving / rejecting a quotation (PATCH /api/quotations/{id}/status). Checked IN the service, so they
 * hold no matter how the controller annotations are enforced.
 *
 * <ol>
 *   <li><b>Permission.</b> The caller's role must hold {@code APPROVAL:EDIT} (and {@code QUOTATION:EDIT}, which the
 *       controller asks for). Without it: 403 FORBIDDEN, nothing is loaded or written.</li>
 *   <li><b>Own tenant only.</b> Enforced by QuotationServiceImpl.findForWrite / TenantResolver.assertWritable: a
 *       quotation of another partner is a 404, exactly like a missing one. A Super Admin looking at a partner through
 *       ?viewPartnerId= is on a GET only; the PATCH always runs against the Super Admin's own (PLATFORM) tenant, so
 *       that view is read-only.</li>
 *   <li><b>No self-approval.</b> The user who created a quotation cannot approve or reject it, whatever their role
 *       (Super Admin included): 403 SELF_APPROVAL. A second person with the approval permission must decide. The
 *       creator can still edit or delete their own PENDING quotation. For a partner with a single user, set
 *       {@code approval.self-approval-allowed=true} (default false).</li>
 *   <li><b>Closed quotations stay closed.</b> A PAID or EXPIRED quotation cannot be approved or rejected (409-style
 *       BUSINESS_RULE), whatever the UI shows.</li>
 * </ol>
 *
 * Logging: refusals are WARN with ids only ("[APPROVAL] denied ..."); the successful decision is logged at INFO by
 * QuotationServiceImpl. Names, e-mails and mobiles never appear.
 */
@Component
public class ApprovalGuard {

    public static final String APPROVAL_PERMISSION = "APPROVAL:EDIT";
    private static final Set<QuotationStatus> CLOSED = Set.of(QuotationStatus.PAID, QuotationStatus.EXPIRED);

    private static final Logger log = LoggerFactory.getLogger(ApprovalGuard.class);

    private final PermissionCache permissionCache;
    private final boolean selfApprovalAllowed;

    public ApprovalGuard(PermissionCache permissionCache,
                         @Value("${approval.self-approval-allowed:false}") boolean selfApprovalAllowed) {
        this.permissionCache = permissionCache;
        this.selfApprovalAllowed = selfApprovalAllowed;
    }

    /** Step 1, before anything is loaded: does the caller's role hold the approval permission? */
    public void assertMayApprove(Long quotationId) {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            log.warn("[APPROVAL] denied, not authenticated quotationId={}", quotationId);
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        Set<String> granted = permissionCache.permissionsForRole(u.getRoleId());
        if (!granted.contains(APPROVAL_PERMISSION)) {
            log.warn("[APPROVAL] denied, missing {} quotationId={} {}", APPROVAL_PERMISSION, quotationId, Actors.describe());
            throw new ForbiddenException("FORBIDDEN", "Missing permission " + APPROVAL_PERMISSION);
        }
    }

    /**
     * Step 2, after the quotation passed the tenant check: state and self-approval rules.
     * Only a decision (next = ACCEPTED / REJECTED) is subject to the self-approval rule.
     */
    public void assertMayDecide(Long quotationId, Long createdBy, Long partnerId,
                                QuotationStatus current, QuotationStatus next) {
        if (CLOSED.contains(current)) {
            log.warn("[APPROVAL] denied, quotation is closed quotationId={} status={} partnerId={} {}",
                    quotationId, current, partnerId, Actors.describe());
            throw new BusinessException("BUSINESS_RULE", "A " + current.name().toLowerCase() + " quotation can no longer be reviewed");
        }
        boolean decision = next == QuotationStatus.ACCEPTED || next == QuotationStatus.REJECTED;
        CurrentUser u = TenantContext.get();
        if (decision && !selfApprovalAllowed && u != null && createdBy != null && createdBy.equals(u.getUserId())) {
            log.warn("[APPROVAL] denied, self-approval quotationId={} partnerId={} {}",
                    quotationId, partnerId, Actors.describe());
            throw new ForbiddenException("SELF_APPROVAL", "You cannot approve or reject a quotation you created");
        }
    }
}