package com.solar.Quotation.dto.requestdto;

/** Query parameters of GET /api/users (page, size, sort, search, partnerId come from PageFilterRequestDto). */
public class UserFilterRequestDto extends PageFilterRequestDto {

    private String roleCode;
    private String status;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
