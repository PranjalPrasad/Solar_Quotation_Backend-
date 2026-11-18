package com.solar.Quotation.util;

import java.util.List;
import java.util.Map;

/**
 * The audit API now receives before/after snapshots instead of a ready-made changes list. Existing tests assert
 * on field changes, so they turn the captured snapshots back into the raw changes list with the same DiffUtil rules
 * (no masking: masking is covered by AuditMaskerTest and AuditServiceImplTest).
 */
public final class AuditTestSupport {
    private AuditTestSupport() { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Map<String, String>> changes(Map before, Map after) {
        if (after == null) return DiffUtil.removed(before);
        return DiffUtil.diff(before, after);
    }
}