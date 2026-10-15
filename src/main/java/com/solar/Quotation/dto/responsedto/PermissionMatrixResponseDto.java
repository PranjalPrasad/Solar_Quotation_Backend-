package com.solar.Quotation.dto.responsedto;

import java.util.List;
import java.util.Map;

public class PermissionMatrixResponseDto {

    private List<ModuleResponseDto> modules;
    private List<RoleGrant> roles;

    public List<ModuleResponseDto> getModules() { return modules; }
    public void setModules(List<ModuleResponseDto> modules) { this.modules = modules; }
    public List<RoleGrant> getRoles() { return roles; }
    public void setRoles(List<RoleGrant> roles) { this.roles = roles; }

    /** One role row of the matrix: granted = module code to list of actions. */
    public static class RoleGrant {
        private Long id;
        private String code;
        private String name;
        private boolean system;
        private Map<String, List<String>> granted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isSystem() { return system; }
        public void setSystem(boolean system) { this.system = system; }
        public Map<String, List<String>> getGranted() { return granted; }
        public void setGranted(Map<String, List<String>> granted) { this.granted = granted; }
    }
}