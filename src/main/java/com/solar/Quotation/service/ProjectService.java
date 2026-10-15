package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.ProjectFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectStatusRequestDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.ProjectResponseDto;
import com.solar.Quotation.dto.responsedto.ProjectSummaryResponseDto;

public interface ProjectService {

    PageResponse<ProjectResponseDto> list(ProjectFilterRequestDto filter);

    /** partnerId is honoured for Super Admin only. */
    ProjectSummaryResponseDto summary(Long partnerId);

    ProjectResponseDto get(Long id);

    ProjectResponseDto create(ProjectRequestDto req);

    ProjectResponseDto update(Long id, ProjectRequestDto req);

    ProjectResponseDto updateStatus(Long id, ProjectStatusRequestDto req);

    void delete(Long id);
}
