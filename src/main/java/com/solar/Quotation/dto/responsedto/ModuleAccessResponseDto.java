package com.solar.Quotation.dto.responsedto;

import java.util.List;

/** One sidebar module the caller may open, with the actions allowed on it. */
public class ModuleAccessResponseDto {

    private String code;
    private String name;
    private String route;
    private List<String> actions;

    public ModuleAccessResponseDto() { }

    public ModuleAccessResponseDto(String code, String name, String route, List<String> actions) {
        this.code = code; this.name = name; this.route = route; this.actions = actions;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }
}
