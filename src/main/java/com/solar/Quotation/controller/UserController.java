package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.AdminResetPasswordRequestDto;
import com.solar.Quotation.dto.requestdto.UserFilterRequestDto;
import com.solar.Quotation.dto.requestdto.UserRequestDto;
import com.solar.Quotation.dto.requestdto.UserStatusRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.RoleRefResponseDto;
import com.solar.Quotation.dto.responsedto.UserResponseDto;
import com.solar.Quotation.dto.responsedto.UserSummaryResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.UserService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Users. A partner admin only ever sees and manages the users of their own partner; Super Admin sees everyone
 * and can filter with ?partnerId=. Scoping is done in UserServiceImpl through TenantResolver.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("USER:VIEW")
    public ApiResponse<PageResponse<UserResponseDto>> list(UserFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    @GetMapping("/summary")
    @RequirePermission("USER:VIEW")
    public ApiResponse<UserSummaryResponseDto> summary() {
        return ApiResponse.ok(service.summary());
    }

    @GetMapping("/role-options")
    @RequirePermission("USER:CREATE")
    public ApiResponse<List<RoleRefResponseDto>> roleOptions() {
        return ApiResponse.ok(service.roleOptions());
    }

    @GetMapping("/{id}")
    @RequirePermission("USER:VIEW")
    public ApiResponse<UserResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @RequirePermission("USER:CREATE")
    public ResponseEntity<ApiResponse<UserResponseDto>> create(@Valid @RequestBody UserRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req), "User created"));
    }

    @PutMapping("/{id}")
    @RequirePermission("USER:EDIT")
    public ApiResponse<UserResponseDto> update(@PathVariable Long id, @Valid @RequestBody UserRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "User updated");
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("USER:EDIT")
    public ApiResponse<UserResponseDto> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequestDto req) {
        return ApiResponse.ok(service.updateStatus(id, req), "Status updated");
    }

    @PostMapping("/{id}/reset-password")
    @RequirePermission("USER:RESET_PASSWORD")
    public ApiResponse<UserResponseDto> resetPassword(@PathVariable Long id,
                                                      @Valid @RequestBody(required = false) AdminResetPasswordRequestDto req) {
        return ApiResponse.ok(service.resetPassword(id, req == null ? new AdminResetPasswordRequestDto() : req),
                "Password reset");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("USER:DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "User deleted");
    }
}
