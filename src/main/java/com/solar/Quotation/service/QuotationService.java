package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.QuotationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationStatusRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.QuotationListItemResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationSummaryResponseDto;

import java.util.List;

public interface QuotationService {

    PageResponse<QuotationListItemResponseDto> list(QuotationFilterRequestDto filter);

    /** partnerId is honoured for Super Admin only. */
    QuotationSummaryResponseDto summary(Long partnerId);

    QuotationResponseDto get(Long id);

    /** Calculates everything (totals, GST split, subsidy, savings) without saving. Used by the wizard preview. */
    QuotationResponseDto preview(QuotationRequestDto req);

    QuotationResponseDto create(QuotationRequestDto req);

    /** Only a PENDING quotation can be edited. */
    QuotationResponseDto update(Long id, QuotationRequestDto req);

    QuotationResponseDto updateStatus(Long id, QuotationStatusRequestDto req);

    void delete(Long id);

    /** Change history of one quotation. The tenant check runs first, so another partner's id gives 404. */
    List<AuditLogResponseDto> history(Long id);
}
