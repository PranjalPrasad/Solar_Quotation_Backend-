package com.solar.Quotation.dto.requestdto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** What GET /api/quotations/{id}/history returns (inside ApiResponse.data). The frontend reads exactly these names. */
public record QuotationHistoryDto(
        Long id,
        int version,
        String action,          // CREATED | UPDATED | STATUS_CHANGED | CONVERTED | DELETED
        String summary,
        ChangedBy changedBy,
        Instant changedAt,
        String status,
        BigDecimal totalAmount,
        List<Change> changes) {

    public record ChangedBy(Long id, String name, String role) { }

    /** field is a dotted path such as "installationCost" or "customer.mobile"; values are display strings. */
    public record Change(String field, String oldValue, String newValue) { }
}
