package com.solar.Quotation.dto.responsedto;

/** Small role reference used inside user and login responses. */
public class RoleRefResponseDto {

    private Long id;
    private String code;
    private String name;
    private String dataScope;

    public RoleRefResponseDto() { }

    public RoleRefResponseDto(Long id, String code, String name, String dataScope) {
        this.id = id; this.code = code; this.name = name; this.dataScope = dataScope;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
}
