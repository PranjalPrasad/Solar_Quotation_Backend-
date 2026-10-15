package com.solar.Quotation.dto.requestdto;

/** Query parameters of GET /api/channel-partners (page, size, sort, search come from PageFilterRequestDto). */
public class ChannelPartnerFilterRequestDto extends PageFilterRequestDto {

    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
