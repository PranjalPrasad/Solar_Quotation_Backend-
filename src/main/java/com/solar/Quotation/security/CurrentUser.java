package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;

public class CurrentUser {

    private Long userId;
    private String name;
    private Long roleId;
    private String roleCode;
    private DataScope dataScope;
    private Long partnerId;          // the caller's OWN tenant. For Super Admin this is the PLATFORM partner.
    private Long viewPartnerId;      // ?viewPartnerId= of a GET request; only honoured for data scope ALL
    private boolean readRequest;     // true only for GET/HEAD; set by JwtAuthFilter, so views never apply to writes

    public CurrentUser() { }
    public CurrentUser(Long userId, String name, Long roleId, String roleCode, DataScope dataScope, Long partnerId) {
        this.userId = userId; this.name = name; this.roleId = roleId;
        this.roleCode = roleCode; this.dataScope = dataScope; this.partnerId = partnerId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public DataScope getDataScope() { return dataScope; }
    public void setDataScope(DataScope dataScope) { this.dataScope = dataScope; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public Long getViewPartnerId() { return viewPartnerId; }
    public void setViewPartnerId(Long viewPartnerId) { this.viewPartnerId = viewPartnerId; }
    public boolean isReadRequest() { return readRequest; }
    public void setReadRequest(boolean readRequest) { this.readRequest = readRequest; }
    public boolean isSuperAdmin() { return dataScope == DataScope.ALL; }
}