package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.dto.requestdto.ProductRequestDto;
import com.solar.Quotation.entity.Product;
import com.solar.Quotation.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure rules of the product module (no Spring, no database). Same package so package-private helpers are visible. */
class ProductRulesTest {

    private final ProductServiceImpl service = new ProductServiceImpl(null, null, null, null, null, null, null);

    @Test
    void priceIsMrpMinusDiscountWhenBothAreGiven() {
        ProductRequestDto req = new ProductRequestDto();
        req.setMrp(new BigDecimal("13800"));
        req.setDiscountPercent(new BigDecimal("9"));
        req.setPrice(new BigDecimal("1"));                 // ignored
        assertEquals(new BigDecimal("12558.00"), service.computePrice(req));
    }

    @Test
    void sentPriceIsUsedWhenMrpOrDiscountIsMissing() {
        ProductRequestDto req = new ProductRequestDto();
        req.setPrice(new BigDecimal("100"));
        assertEquals(new BigDecimal("100.00"), service.computePrice(req));
    }

    @Test
    void priceIsRequiredWhenThereIsNothingToCompute() {
        assertThrows(BusinessException.class, () -> service.computePrice(new ProductRequestDto()));
    }

    @Test
    void stockStatusFollowsStockAndThreshold() {
        Product p = new Product();
        p.setLowStockThreshold(10);
        p.setStock(0);
        assertEquals("OUT_OF_STOCK", ProductServiceImpl.stockStatus(p));
        p.setStock(10);
        assertEquals("LOW_STOCK", ProductServiceImpl.stockStatus(p));
        p.setStock(11);
        assertEquals("IN_STOCK", ProductServiceImpl.stockStatus(p));
    }
}
