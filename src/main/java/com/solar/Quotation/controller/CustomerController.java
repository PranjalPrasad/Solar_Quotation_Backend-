package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.CustomerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupFilterRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerSummaryResponseDto;
import com.solar.Quotation.dto.responsedto.FollowupResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.CustomerService;
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

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("CUSTOMER:VIEW")
    public ApiResponse<PageResponse<CustomerResponseDto>> list(CustomerFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    @GetMapping("/summary")
    @RequirePermission("CUSTOMER:VIEW")
    public ApiResponse<CustomerSummaryResponseDto> summary(@RequestParam(required = false) Long partnerId) {
        return ApiResponse.ok(service.summary(partnerId));
    }

    /** The follow-up table (all visible customers). Declared before /{id} so it is not read as an id. */
    @GetMapping("/followups")
    @RequirePermission("CUSTOMER:VIEW")
    public ApiResponse<PageResponse<FollowupResponseDto>> allFollowups(FollowupFilterRequestDto filter) {
        return ApiResponse.ok(service.allFollowups(filter));
    }

    @GetMapping("/{id}")
    @RequirePermission("CUSTOMER:VIEW")
    public ApiResponse<CustomerResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @RequirePermission("CUSTOMER:CREATE")
    public ResponseEntity<ApiResponse<CustomerResponseDto>> create(@Valid @RequestBody CustomerRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req), "Customer created"));
    }

    @PutMapping("/{id}")
    @RequirePermission("CUSTOMER:EDIT")
    public ApiResponse<CustomerResponseDto> update(@PathVariable Long id, @Valid @RequestBody CustomerRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "Customer updated");
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("CUSTOMER:EDIT")
    public ApiResponse<CustomerResponseDto> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody CustomerStatusRequestDto req) {
        return ApiResponse.ok(service.updateStatus(id, req), "Status updated");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("CUSTOMER:DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Customer deleted");
    }

    @GetMapping("/{id}/followups")
    @RequirePermission("CUSTOMER:VIEW")
    public ApiResponse<List<FollowupResponseDto>> followups(@PathVariable Long id) {
        return ApiResponse.ok(service.followups(id));
    }

    @PostMapping("/{id}/followups")
    @RequirePermission("CUSTOMER:EDIT")
    public ResponseEntity<ApiResponse<FollowupResponseDto>> addFollowup(@PathVariable Long id,
                                                                        @Valid @RequestBody FollowupRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.addFollowup(id, req), "Follow-up added"));
    }

    @PatchMapping("/{id}/followups/{followupId}")
    @RequirePermission("CUSTOMER:EDIT")
    public ApiResponse<FollowupResponseDto> updateFollowup(@PathVariable Long id, @PathVariable Long followupId,
                                                           @Valid @RequestBody FollowupRequestDto req) {
        return ApiResponse.ok(service.updateFollowup(id, followupId, req), "Follow-up updated");
    }

    @GetMapping("/{id}/history")
    @RequirePermission("CUSTOMER:VIEW")
    public ApiResponse<List<AuditLogResponseDto>> history(@PathVariable Long id) {
        return ApiResponse.ok(service.history(id));
    }
}
