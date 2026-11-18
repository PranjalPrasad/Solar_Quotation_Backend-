package com.solar.Quotation.util;

import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Stops a record (branding logoPath, user photoPath) from pointing at anything but a file the caller's own partner
 * uploaded. The download endpoint is tenant-checked anyway; this keeps foreign or hand-made values out of the database.
 * An UNCHANGED value is always accepted, so records that still hold an older path can be saved without re-uploading.
 */
public final class FileReferenceGuard {

    private static final Logger log = LoggerFactory.getLogger(FileReferenceGuard.class);

    private FileReferenceGuard() { }

    /** @param extraPartnerIds other partners whose files are acceptable (for example the partner a Super Admin creates a user in) */
    public static void require(String newPath, String currentPath, String label, Long... extraPartnerIds) {
        String candidate = newPath == null ? "" : newPath.trim();
        if (candidate.isEmpty() || candidate.equals(currentPath == null ? "" : currentPath.trim())) return;

        CurrentUser user = TenantContext.get();
        List<Long> allowed = new ArrayList<>();
        if (user != null && user.getPartnerId() != null) allowed.add(user.getPartnerId());
        if (extraPartnerIds != null) {
            for (Long id : extraPartnerIds) if (id != null) allowed.add(id);
        }
        if (!FilePaths.isReferenceAllowed(candidate, allowed)) {
            log.warn("[FILE] reference rejected field={} userId={} partnerId={}", label,
                    user == null ? null : user.getUserId(), user == null ? null : user.getPartnerId());
            throw new BusinessException("VALIDATION_ERROR", label + " must be a file uploaded through the upload button");
        }
    }
}
