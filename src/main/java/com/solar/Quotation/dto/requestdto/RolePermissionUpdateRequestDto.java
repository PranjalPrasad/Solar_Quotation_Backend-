package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** Replaces the whole permission set of a role. Key = module code, value = granted actions. */
public class RolePermissionUpdateRequestDto {

    @NotNull(message = "granted is required")
    private Map<String, List<String>> granted;

    public Map<String, List<String>> getGranted() { return granted; }
    public void setGranted(Map<String, List<String>> granted) { this.granted = granted; }
}
