package com.solar.Quotation.dto.responsedto;

import java.util.List;

/** GET /api/auth/me: the user, the modules to show and the flat permission list. */
public class CurrentUserResponseDto {

    private UserInfoResponseDto user;
    private List<ModuleAccessResponseDto> modules;
    private List<String> permissions;

    public CurrentUserResponseDto() { }

    public CurrentUserResponseDto(UserInfoResponseDto user, List<ModuleAccessResponseDto> modules, List<String> permissions) {
        this.user = user; this.modules = modules; this.permissions = permissions;
    }

    public UserInfoResponseDto getUser() { return user; }
    public void setUser(UserInfoResponseDto user) { this.user = user; }
    public List<ModuleAccessResponseDto> getModules() { return modules; }
    public void setModules(List<ModuleAccessResponseDto> modules) { this.modules = modules; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
