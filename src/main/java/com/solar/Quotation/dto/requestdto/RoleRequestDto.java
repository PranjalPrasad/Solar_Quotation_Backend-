package com.solar.Quotation.dto.requestdto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** code is required on create and ignored on update. dataScope: PARTNER or OWN (ALL is reserved for SUPER_ADMIN). */
public class RoleRequestDto {

    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$", message = "Code must be UPPER_SNAKE_CASE, 3 to 50 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name is too long")
    private String name;

    @Size(max = 255, message = "Description is too long")
    private String description;

    @Pattern(regexp = "PARTNER|OWN", message = "Data scope must be PARTNER or OWN")
    private String dataScope;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
}
