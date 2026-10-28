package com.solar.Quotation.service;


import com.solar.Quotation.dto.requestdto.ReferralFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ReferralPaidRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerOptionResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralPartnerTotalsResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralSummaryResponseDto;
import com.solar.Quotation.entity.Quotation;

import java.util.Collection;
import java.util.List;

public interface ReferralService {

    List<PartnerOptionResponseDto> referrerOptions();

    // ---------------------------------------------------------------- API (ReferralController)

    PageResponse<ReferralResponseDto> list(ReferralFilterRequestDto filter);

    /** partnerId and referrerPartnerId are honoured for Super Admin only. */
    ReferralSummaryResponseDto summary(Long partnerId, Long referrerPartnerId);

    ReferralResponseDto get(Long id);

    /** Super Admin only. APPROVED to PAID. */
    ReferralResponseDto markPaid(Long id, ReferralPaidRequestDto req);

    /** Super Admin only. */
    List<AuditLogResponseDto> history(Long id);

    /** Super Admin only. One grouped query for the whole partner list page. */
    List<ReferralPartnerTotalsResponseDto> partnerTotals(Collection<Long> partnerIds);

    // ---------------------------------------------------------------- hooks (called by QuotationServiceImpl,
    // inside the quotation's own transaction)

    /** After a quotation is created or edited: creates, updates or removes its referral row. */
    void syncFromQuotation(Quotation quotation);

    /** After the quotation status changed (also called by the Approval module through QuotationService). */
    void onQuotationStatusChanged(Quotation quotation);

    /** Before a quotation is soft deleted. */
    void onQuotationDeleted(Quotation quotation);
}
