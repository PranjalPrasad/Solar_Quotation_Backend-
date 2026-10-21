package com.solar.Quotation.util;

import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

public final class SubsidyCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SubsidyCalculator() { }

    public static BigDecimal calculate(String category, BigDecimal sizeKw, BigDecimal projectCost,
                                       List<SubsidyRuleResponseDto> rules) {
        if (category == null || sizeKw == null || sizeKw.signum() <= 0 || rules == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SubsidyRuleResponseDto rule : rules) {
            if (!rule.isActive() || !category.equalsIgnoreCase(rule.getCategory())) {
                continue;
            }
            BigDecimal min = rule.getCapMin() == null ? BigDecimal.ZERO : rule.getCapMin();
            BigDecimal max = rule.getCapMax();
            BigDecimal value = rule.getValue() == null ? BigDecimal.ZERO : rule.getValue();
            BigDecimal amount;

            switch (rule.getValueType().toUpperCase(Locale.ROOT)) {
                case "PER_KW" -> {
                    BigDecimal upper = max == null ? sizeKw : sizeKw.min(max);
                    BigDecimal overlap = upper.subtract(min);
                    if (overlap.signum() <= 0) {
                        continue;
                    }
                    amount = value.multiply(overlap);
                }
                case "PERCENTAGE" -> {
                    if (!inRange(sizeKw, min, max)) {
                        continue;
                    }
                    BigDecimal cost = projectCost == null ? BigDecimal.ZERO : projectCost;
                    amount = cost.multiply(value).divide(HUNDRED, 6, RoundingMode.HALF_UP);
                }
                case "AMOUNT" -> {
                    if (!inRange(sizeKw, min, max)) {
                        continue;
                    }
                    amount = value;
                }
                default -> {
                    continue;
                }
            }

            if (rule.getMaxAmount() != null && amount.compareTo(rule.getMaxAmount()) > 0) {
                amount = rule.getMaxAmount();
            }
            total = total.add(amount);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean inRange(BigDecimal sizeKw, BigDecimal min, BigDecimal max) {
        return sizeKw.compareTo(min) > 0 && (max == null || sizeKw.compareTo(max) <= 0);
    }
}
