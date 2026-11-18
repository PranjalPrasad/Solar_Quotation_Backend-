package com.solar.Quotation.dto.responsedto;

import java.util.List;

/** What the signed-in user may see: the frontend builds its sidebar from this. */
public class AccessResponseDto {

    private String roleCode;
    private String dataScope;
    private List<ModuleResponseDto> modules;
    private List<String> permissions;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public List<ModuleResponseDto> getModules() { return modules; }
    public void setModules(List<ModuleResponseDto> modules) { this.modules = modules; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}