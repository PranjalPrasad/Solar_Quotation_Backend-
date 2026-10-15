package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.CustomerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupFilterRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerSummaryResponseDto;
import com.solar.Quotation.dto.responsedto.FollowupResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;

import java.util.List;

public interface CustomerService {

    PageResponse<CustomerResponseDto> list(CustomerFilterRequestDto filter);

    /** partnerId is honoured for Super Admin only. */
    CustomerSummaryResponseDto summary(Long partnerId);

    CustomerResponseDto get(Long id);

    CustomerResponseDto create(CustomerRequestDto req);

    CustomerResponseDto update(Long id, CustomerRequestDto req);

    CustomerResponseDto updateStatus(Long id, CustomerStatusRequestDto req);

    void delete(Long id);

    List<FollowupResponseDto> followups(Long customerId);

    FollowupResponseDto addFollowup(Long customerId, FollowupRequestDto req);

    FollowupResponseDto updateFollowup(Long customerId, Long followupId, FollowupRequestDto req);

    /** The follow-up table: follow-ups of all visible customers. */
    PageResponse<FollowupResponseDto> allFollowups(FollowupFilterRequestDto filter);

    /** Timeline of one customer, built from the audit log. */
    List<AuditLogResponseDto> history(Long customerId);
}
