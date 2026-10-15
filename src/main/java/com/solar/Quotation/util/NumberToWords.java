package com.solar.Quotation.util;


/** Indian numbering (Crore, Lakh, Thousand). Used for "amountInWords" on quotations. */
public final class NumberToWords {
    private NumberToWords() { }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    public static String rupees(long n) {
        if (n == 0) return "Zero Rupees Only";
        return convert(n) + " Rupees Only";
    }

    private static String convert(long n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 10_000_000L) { sb.append(convert(n / 10_000_000L)).append(" Crore "); n %= 10_000_000L; }
        if (n >= 100_000L) { sb.append(convert(n / 100_000L)).append(" Lakh "); n %= 100_000L; }
        if (n >= 1_000L) { sb.append(convert(n / 1_000L)).append(" Thousand "); n %= 1_000L; }
        if (n >= 100L) { sb.append(ONES[(int) (n / 100)]).append(" Hundred "); n %= 100L; }
        if (n > 0) {
            if (n < 20) sb.append(ONES[(int) n]);
            else {
                sb.append(TENS[(int) (n / 10)]);
                if (n % 10 > 0) sb.append(' ').append(ONES[(int) (n % 10)]);
            }
        }
        return sb.toString().trim();
    }
}
