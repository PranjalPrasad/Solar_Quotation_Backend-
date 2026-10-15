package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;

public class CurrentUser {

    private Long userId;
    private String name;
    private Long roleId;
    private String roleCode;
    private DataScope dataScope;
    private Long partnerId;

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
    public boolean isSuperAdmin() { return dataScope == DataScope.ALL; }
}
