package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.ChannelPartnerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.UserFilterRequestDto;
import com.solar.Quotation.dto.responsedto.ChannelPartnerResponseDto;
import com.solar.Quotation.dto.responsedto.ChannelPartnerStatsResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerOptionResponseDto;
import com.solar.Quotation.dto.responsedto.UserResponseDto;

import java.util.List;

public interface ChannelPartnerService {

    PageResponse<ChannelPartnerResponseDto> list(ChannelPartnerFilterRequestDto filter);

    List<PartnerOptionResponseDto> options();

    ChannelPartnerResponseDto get(Long id);

    /** Creates the partner and its first CHANNEL_PARTNER_ADMIN user in one transaction. */
    ChannelPartnerResponseDto create(ChannelPartnerRequestDto req);

    ChannelPartnerResponseDto update(Long id, ChannelPartnerRequestDto req);

    ChannelPartnerResponseDto updateStatus(Long id, ChannelPartnerStatusRequestDto req);

    ChannelPartnerStatsResponseDto stats(Long id);

    PageResponse<UserResponseDto> users(Long id, UserFilterRequestDto filter);
}