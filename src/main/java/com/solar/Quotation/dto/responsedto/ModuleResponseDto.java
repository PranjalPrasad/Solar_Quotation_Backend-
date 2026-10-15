package com.solar.Quotation.dto.responsedto;

import java.util.List;

public class ModuleResponseDto {

    private String code;
    private String name;
    private String route;
    private boolean platformOnly;
    private int sortOrder;
    private List<String> actions;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public boolean isPlatformOnly() { return platformOnly; }
    public void setPlatformOnly(boolean platformOnly) { this.platformOnly = platformOnly; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }
}
