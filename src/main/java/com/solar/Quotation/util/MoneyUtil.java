package com.solar.Quotation.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {
    private MoneyUtil() { }

    public static final BigDecimal HUNDRED = new BigDecimal("100");

    public static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    public static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return scale(nz(base).multiply(nz(percent)).divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) { return a.compareTo(b) >= 0 ? a : b; }
    public static BigDecimal min(BigDecimal a, BigDecimal b) { return a.compareTo(b) <= 0 ? a : b; }
}
