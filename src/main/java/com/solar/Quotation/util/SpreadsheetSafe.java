package com.solar.Quotation.util;

public final class SpreadsheetSafe {

    private SpreadsheetSafe() { }

    public static String text(String value) {
        if (value == null || value.isEmpty()) return value;
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' || c == '\n') {
            return "'" + value;
        }
        return value;
    }
}