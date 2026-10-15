package com.solar.Quotation.controller;
import com.solar.Quotation.dto.requestdto.RolePermissionUpdateRequestDto;
import com.solar.Quotation.dto.requestdto.RoleRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.ModuleResponseDto;
import com.solar.Quotation.dto.responsedto.PermissionMatrixResponseDto;
import com.solar.Quotation.dto.responsedto.RoleResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Super Admin only (ROLE is a platform-only module). */
@RestController
@RequestMapping("/api")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    @RequirePermission("ROLE:VIEW")
    public ApiResponse<List<RoleResponseDto>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping("/roles")
    @RequirePermission("ROLE:EDIT")
    public ResponseEntity<ApiResponse<RoleResponseDto>> create(@Valid @RequestBody RoleRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req), "Role created"));
    }

    @PutMapping("/roles/{id}")
    @RequirePermission("ROLE:EDIT")
    public ApiResponse<RoleResponseDto> update(@PathVariable Long id, @Valid @RequestBody RoleRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "Role updated");
    }

    @DeleteMapping("/roles/{id}")
    @RequirePermission("ROLE:EDIT")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Role deleted");
    }

    @GetMapping("/modules")
    @RequirePermission("ROLE:VIEW")
    public ApiResponse<List<ModuleResponseDto>> modules() {
        return ApiResponse.ok(service.modules());
    }

    @GetMapping("/roles/permission-matrix")
    @RequirePermission("ROLE:VIEW")
    public ApiResponse<PermissionMatrixResponseDto> matrix() {
        return ApiResponse.ok(service.matrix());
    }

    @PutMapping("/roles/{id}/permissions")
    @RequirePermission("ROLE:EDIT")
    public ApiResponse<PermissionMatrixResponseDto.RoleGrant> updatePermissions(
            @PathVariable Long id, @Valid @RequestBody RolePermissionUpdateRequestDto req) {
        return ApiResponse.ok(service.updatePermissions(id, req), "Permissions updated");
    }
}
