package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.ProductFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProductRequestDto;
import com.solar.Quotation.dto.requestdto.ProductStatusRequestDto;
import com.solar.Quotation.dto.requestdto.StockAdjustRequestDto;
import com.solar.Quotation.dto.requestdto.StockMovementRequestDto;
import com.solar.Quotation.dto.responsedto.StockMovementResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.ProductCatalogItemResponseDto;
import com.solar.Quotation.dto.responsedto.ProductResponseDto;
import com.solar.Quotation.dto.responsedto.ProductSummaryResponseDto;

import java.util.List;

public interface ProductService {

    PageResponse<ProductResponseDto> list(ProductFilterRequestDto filter);

    /**
     * Reads (list, catalog, summary, get) use TenantResolver.effectiveReadPartnerId(): the caller's own tenant, or,
     * for Super Admin on a GET, the partner named by ?viewPartnerId=. Writes always use the caller's own tenant.
     */

    /** Active products for the quotation picker. */
    List<ProductCatalogItemResponseDto> catalog();

    ProductSummaryResponseDto summary();

    ProductResponseDto get(Long id);

    ProductResponseDto create(ProductRequestDto req);

    ProductResponseDto update(Long id, ProductRequestDto req);

    ProductResponseDto updateStatus(Long id, ProductStatusRequestDto req);

    ProductResponseDto adjustStock(Long id, StockAdjustRequestDto req);

    /** Stock ledger of one product, newest first. Own tenant only (stock movements are never read across tenants). */
    List<StockMovementResponseDto> stockHistory(Long id);

    /** Records a stock IN / OUT line and updates the product stock. */
    List<StockMovementResponseDto> addStockMovement(Long id, StockMovementRequestDto req);

    void delete(Long id);
}