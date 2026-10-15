package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.ProductFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProductRequestDto;
import com.solar.Quotation.dto.requestdto.ProductStatusRequestDto;
import com.solar.Quotation.dto.requestdto.StockAdjustRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.ProductCatalogItemResponseDto;
import com.solar.Quotation.dto.responsedto.ProductResponseDto;
import com.solar.Quotation.dto.responsedto.ProductSummaryResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.ProductService;
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
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("PRODUCT:VIEW")
    public ApiResponse<PageResponse<ProductResponseDto>> list(ProductFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    /** Active products for the quotation product picker. Super Admin passes ?partnerId= to pick a partner. */
    @GetMapping("/catalog")
    @RequirePermission("PRODUCT:VIEW")
    public ApiResponse<List<ProductCatalogItemResponseDto>> catalog(@RequestParam(required = false) Long partnerId) {
        return ApiResponse.ok(service.catalog(partnerId));
    }

    @GetMapping("/summary")
    @RequirePermission("PRODUCT:VIEW")
    public ApiResponse<ProductSummaryResponseDto> summary(@RequestParam(required = false) Long partnerId) {
        return ApiResponse.ok(service.summary(partnerId));
    }

    @GetMapping("/{id}")
    @RequirePermission("PRODUCT:VIEW")
    public ApiResponse<ProductResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @RequirePermission("PRODUCT:CREATE")
    public ResponseEntity<ApiResponse<ProductResponseDto>> create(@Valid @RequestBody ProductRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req), "Product created"));
    }

    @PutMapping("/{id}")
    @RequirePermission("PRODUCT:EDIT")
    public ApiResponse<ProductResponseDto> update(@PathVariable Long id, @Valid @RequestBody ProductRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "Product updated");
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("PRODUCT:EDIT")
    public ApiResponse<ProductResponseDto> updateStatus(@PathVariable Long id,
                                                        @Valid @RequestBody ProductStatusRequestDto req) {
        return ApiResponse.ok(service.updateStatus(id, req), "Status updated");
    }

    @PatchMapping("/{id}/stock")
    @RequirePermission("PRODUCT:EDIT")
    public ApiResponse<ProductResponseDto> adjustStock(@PathVariable Long id,
                                                       @Valid @RequestBody StockAdjustRequestDto req) {
        return ApiResponse.ok(service.adjustStock(id, req), "Stock updated");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("PRODUCT:DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Product deleted");
    }
}
