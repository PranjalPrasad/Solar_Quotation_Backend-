package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.ProjectFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectStatusRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.ProjectResponseDto;
import com.solar.Quotation.dto.responsedto.ProjectSummaryResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("PROJECT:VIEW")
    public ApiResponse<PageResponse<ProjectResponseDto>> list(ProjectFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    @GetMapping("/summary")
    @RequirePermission("PROJECT:VIEW")
    public ApiResponse<ProjectSummaryResponseDto> summary(@RequestParam(required = false) Long partnerId) {
        return ApiResponse.ok(service.summary(partnerId));
    }

    @GetMapping("/{id}")
    @RequirePermission("PROJECT:VIEW")
    public ApiResponse<ProjectResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @RequirePermission("PROJECT:CREATE")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> create(@Valid @RequestBody ProjectRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req), "Project created"));
    }

    @PutMapping("/{id}")
    @RequirePermission("PROJECT:EDIT")
    public ApiResponse<ProjectResponseDto> update(@PathVariable Long id, @Valid @RequestBody ProjectRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "Project updated");
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("PROJECT:EDIT")
    public ApiResponse<ProjectResponseDto> updateStatus(@PathVariable Long id,
                                                        @Valid @RequestBody ProjectStatusRequestDto req) {
        return ApiResponse.ok(service.updateStatus(id, req), "Status updated");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("PROJECT:DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Project deleted");
    }
}
