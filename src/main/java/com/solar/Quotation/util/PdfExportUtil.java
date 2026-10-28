package com.solar.Quotation.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.solar.Quotation.dto.responsedto.ReportRowResponseDto;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Function;

/** Builds the PDF files for the report export. */
public final class PdfExportUtil {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 8);

    private PdfExportUtil() {}

    public static byte[] quotations(List<ReportRowResponseDto.Quotation> rows) {
        return build("Quotation Report",
                new String[]{"Quote No", "Customer", "kW", "Amount", "Sales Person", "Status", "Date", "Partner"},
                rows,
                r -> new Object[]{r.getQuoteNo(), r.getCustomer(), r.getSystemSizeKw(), r.getAmount(),
                        r.getSalesPerson(), r.getStatus(), r.getDate(), r.getPartnerName()});
    }

    public static byte[] gst(List<ReportRowResponseDto.Gst> rows) {
        return build("GST Report",
                new String[]{"Quote No", "Customer", "Taxable", "GST %", "CGST", "SGST", "GST", "Total", "Date"},
                rows,
                r -> new Object[]{r.getQuoteNo(), r.getCustomer(), r.getTaxableAmount(), r.getGstPercent(),
                        r.getCgstAmount(), r.getSgstAmount(), r.getGstAmount(), r.getTotal(), r.getDate()});
    }

    public static byte[] referral(List<ReportRowResponseDto.ReferralIncome> rows) {
        return build("Referral Income Report",
                new String[]{"Referrer", "Referred Customer", "Quotation Amount", "Referral %", "Referral Amount", "Status"},
                rows,
                r -> new Object[]{r.getReferrerName(), r.getReferredCustomer(), r.getQuotationAmount(),
                        r.getReferralPercent(), r.getReferralAmount(), r.getStatus()});
    }

    private static <T> byte[] build(String title, String[] headers, List<T> rows, Function<T, Object[]> mapper) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Paragraph p = new Paragraph(title, TITLE);
            p.setSpacingAfter(10);
            doc.add(p);

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, HEAD));
                c.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            }
            for (T item : rows) {
                for (Object v : mapper.apply(item)) {
                    table.addCell(new PdfPCell(new Phrase(v == null ? "" : v.toString(), BODY)));
                }
            }
            doc.add(table);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the PDF report", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }
}
