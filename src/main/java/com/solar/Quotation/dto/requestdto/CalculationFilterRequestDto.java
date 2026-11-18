package com.solar.Quotation.dto.requestdto;

/**
 * List filter (search, projectType, page, size, sort come from the query string).
 * partnerId is inherited from PageFilterRequestDto and is IGNORED for reads: a Super Admin looks at one partner
 * through ?viewPartnerId= (TenantResolver), never through a filter.
 */
public class CalculationFilterRequestDto extends PageFilterRequestDto {

    private String projectType;

    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
}