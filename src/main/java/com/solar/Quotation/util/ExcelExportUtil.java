package com.solar.Quotation.util;

import com.solar.Quotation.dto.responsedto.ReportRowResponseDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** Builds the .xlsx files for the report export. */
public final class ExcelExportUtil {

    private ExcelExportUtil() {}

    public static byte[] quotations(List<ReportRowResponseDto.Quotation> rows) {
        return build("Quotations",
                new String[]{"Quote No", "Customer", "System Size (kW)", "Amount", "Sales Person", "Status", "Date", "Partner"},
                rows,
                r -> new Object[]{r.getQuoteNo(), r.getCustomer(), r.getSystemSizeKw(), r.getAmount(),
                        r.getSalesPerson(), r.getStatus(), r.getDate(), r.getPartnerName()});
    }

    public static byte[] gst(List<ReportRowResponseDto.Gst> rows) {
        return build("GST",
                new String[]{"Quote No", "Customer", "Taxable Amount", "GST %", "CGST", "SGST", "GST Amount", "Total", "Date"},
                rows,
                r -> new Object[]{r.getQuoteNo(), r.getCustomer(), r.getTaxableAmount(), r.getGstPercent(),
                        r.getCgstAmount(), r.getSgstAmount(), r.getGstAmount(), r.getTotal(), r.getDate()});
    }

    public static byte[] referral(List<ReportRowResponseDto.ReferralIncome> rows) {
        return build("Referral Income",
                new String[]{"Referrer", "Referred Customer", "Quotation Amount", "Referral %", "Referral Amount", "Status"},
                rows,
                r -> new Object[]{r.getReferrerName(), r.getReferredCustomer(), r.getQuotationAmount(),
                        r.getReferralPercent(), r.getReferralAmount(), r.getStatus()});
    }

    private static <T> byte[] build(String sheetName, String[] headers, List<T> rows, Function<T, Object[]> mapper) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(head);
            }

            int rowNum = 1;
            for (T item : rows) {
                Row row = sheet.createRow(rowNum++);
                Object[] values = mapper.apply(item);
                for (int i = 0; i < values.length; i++) {
                    Cell c = row.createCell(i);
                    Object v = values[i];
                    if (v == null) {
                        c.setBlank();
                    } else if (v instanceof BigDecimal bd) {
                        c.setCellValue(bd.doubleValue());
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(v.toString());
                    }
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the Excel report", e);
        }
    }
}
