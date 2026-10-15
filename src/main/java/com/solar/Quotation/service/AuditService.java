package com.solar.Quotation.service;


import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.dto.requestdto.AuditFilterRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.security.CurrentUser;

import java.util.List;
import java.util.Map;

public interface AuditService {

    /** Writes one audit row in the caller's transaction. Actor = user in TenantContext. */
    void record(AuditAction action, String module, String entityType, Long entityId,
                String entityLabel, Long partnerId, List<Map<String, String>> changes);

    /** For login and logout, where TenantContext is not set yet. */
    void recordAs(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                  String entityLabel, Long partnerId, List<Map<String, String>> changes);

    /** Super Admin audit screen. */
    PageResponse<AuditLogResponseDto> search(AuditFilterRequestDto filter);

    /** CSV bytes of the same filter (max 5000 rows). */
    byte[] exportCsv(AuditFilterRequestDto filter);

    /** Per-record history. Partner users get only action, time and actor name. */
    List<AuditLogResponseDto> entityHistory(String entityType, Long entityId);
}
