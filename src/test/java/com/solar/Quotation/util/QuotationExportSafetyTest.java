package com.solar.Quotation.util;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.dto.requestdto.ReportFilterRequestDto;
import com.solar.Quotation.dto.responsedto.ReportRowResponseDto;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.serviceImpl.ReportServiceImpl;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Export: formula escaping, row cap, batch paging and tenant scope. */
class QuotationExportSafetyTest {

    @AfterEach
    void cleanUp() { TenantContext.clear(); }

    // ---------------------------------------------------------------- formula injection

    @Test
    void textStartingWithFormulaCharacters_getsALeadingApostrophe() {
        assertEquals("'=HYPERLINK(\"http://x\")", SpreadsheetSafe.text("=HYPERLINK(\"http://x\")"));
        assertEquals("'+1", SpreadsheetSafe.text("+1"));
        assertEquals("'-2", SpreadsheetSafe.text("-2"));
        assertEquals("'@SUM(A1)", SpreadsheetSafe.text("@SUM(A1)"));
        assertEquals("'\tx", SpreadsheetSafe.text("\tx"));
        assertEquals("Asha", SpreadsheetSafe.text("Asha"));
        assertEquals("SQ-0001", SpreadsheetSafe.text("SQ-0001"));
        assertNull(SpreadsheetSafe.text(null));
    }

    @Test
    void excelCells_areEscapedForText_butNumbersStayNumbers() throws Exception {
        ReportRowResponseDto.Quotation row = new ReportRowResponseDto.Quotation();
        row.setQuoteNo("SQ-1");
        row.setCustomer("=cmd|' /C calc'!A0");
        row.setAmount(new BigDecimal("-500.50"));
        byte[] xlsx = ExcelExportUtil.quotations(List.of(row));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var r = wb.getSheetAt(0).getRow(1);
            assertEquals("'=cmd|' /C calc'!A0", r.getCell(1).getStringCellValue());
            assertEquals(-500.50, r.getCell(3).getNumericCellValue(), 0.001);   // a negative amount is not touched
        }
    }

    @Test
    void utilsRefuseMoreRowsThanTheCeiling() {
        List<ReportRowResponseDto.Quotation> many = new ArrayList<>();
        for (int i = 0; i <= ExcelExportUtil.MAX_ROWS; i++) many.add(new ReportRowResponseDto.Quotation());
        assertThrows(IllegalArgumentException.class, () -> ExcelExportUtil.quotations(many));
        assertThrows(IllegalArgumentException.class, () -> PdfExportUtil.quotations(many));
    }

    // ---------------------------------------------------------------- service: tenant, cap, paging

    private static Object[] row(int i) {
        return new Object[]{"SQ-" + i, "Cust " + i, new BigDecimal("3"), new BigDecimal("1000"), null, "PENDING",
                LocalDate.of(2026, 11, 1), "Partner", (long) i};
    }

    private static void login(DataScope scope, Long partnerId) {
        TenantContext.set(new CurrentUser(7L, "T", 10L, scope == DataScope.ALL ? "SUPER_ADMIN" : "ROLE_X", scope, partnerId));
    }

    @Test
    void export_usesOwnTenant_ignoresFilterPartnerId_andReadsInBatches() {
        QuotationRepository repo = mock(QuotationRepository.class);
        AuditService audit = mock(AuditService.class);
        List<Object[]> all = new ArrayList<>();
        for (int i = 0; i < 1200; i++) all.add(row(i));
        when(repo.quotationReport(any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(6);
            int from = (int) p.getOffset();
            int to = Math.min(from + p.getPageSize(), all.size());
            return new PageImpl<>(all.subList(from, to), p, all.size());
        });
        ReportServiceImpl service = new ReportServiceImpl(repo, new TenantResolver(), audit, 5000);
        login(DataScope.PARTNER, 12L);

        ReportFilterRequestDto f = new ReportFilterRequestDto();
        f.setPartnerId(99L);                                  // another partner: must be ignored
        byte[] body = service.export(f, "QUOTATIONS", "EXCEL");

        assertTrue(body.length > 0);
        ArgumentCaptor<Long> partner = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repo, times(3)).quotationReport(partner.capture(), any(), any(), any(), any(), any(), pageable.capture());
        assertTrue(partner.getAllValues().stream().allMatch(p -> p.equals(12L)));
        assertTrue(pageable.getAllValues().stream().allMatch(p -> p.getPageSize() <= 500));   // 500 + 500 + 200
    }

    @Test
    void export_aboveTheCap_isRefusedInsteadOfCutShort() {
        QuotationRepository repo = mock(QuotationRepository.class);
        AuditService audit = mock(AuditService.class);
        when(repo.quotationReport(any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(6);
            return new PageImpl<>(List.of(row(1)), p, 6000);
        });
        ReportServiceImpl service = new ReportServiceImpl(repo, new TenantResolver(), audit, 5000);
        login(DataScope.PARTNER, 12L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.export(new ReportFilterRequestDto(), "QUOTATIONS", "PDF"));
        assertEquals("EXPORT_TOO_LARGE", ex.getErrorCode());
        verify(repo, times(1)).quotationReport(any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void superAdminExport_isOwnTenantUnlessViewing_andAViewReadsThatPartner() {
        QuotationRepository repo = mock(QuotationRepository.class);
        AuditService audit = mock(AuditService.class);
        when(repo.quotationReport(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(row(1)), inv.<Pageable>getArgument(6), 1));
        ReportServiceImpl service = new ReportServiceImpl(repo, new TenantResolver(), audit, 5000);

        login(DataScope.ALL, 1L);
        service.export(new ReportFilterRequestDto(), "QUOTATIONS", "EXCEL");
        verify(repo).quotationReport(eq(1L), any(), any(), any(), any(), any(), any(Pageable.class));

        CurrentUser u = TenantContext.get();
        u.setReadRequest(true);
        u.setViewPartnerId(12L);
        service.export(new ReportFilterRequestDto(), "QUOTATIONS", "EXCEL");
        verify(repo).quotationReport(eq(12L), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    // ---------------------------------------------------------------- audit, validation, id

    @Test
    void export_writesOneAuditRow_withTypeFormatRowsAndFilters_andNoPersonalData() {
        QuotationRepository repo = mock(QuotationRepository.class);
        AuditService audit = mock(AuditService.class);
        when(repo.quotationReport(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(row(1)), inv.<Pageable>getArgument(6), 1));
        ReportServiceImpl service = new ReportServiceImpl(repo, new TenantResolver(), audit, 5000);
        login(DataScope.PARTNER, 12L);

        ReportFilterRequestDto f = new ReportFilterRequestDto();
        f.setFrom(LocalDate.of(2026, 1, 1));
        f.setTo(LocalDate.of(2026, 11, 17));
        f.setStatus("accepted");
        service.export(f, "QUOTATION", "excel");                      // aliases and case are accepted

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<java.util.Map<String, String>>> changes = ArgumentCaptor.forClass(List.class);
        verify(audit, times(1)).record(eq(AuditAction.EXPORT), eq("REPORT"), eq("Report"), isNull(),
                anyString(), eq(12L), changes.capture());
        String note = String.valueOf(changes.getValue());
        assertTrue(note.contains("type=QUOTATIONS") && note.contains("format=EXCEL") && note.contains("rows=1"));
        assertTrue(note.contains("from=2026-01-01") && note.contains("status=accepted"));
        assertFalse(note.contains("Cust 1"));                         // no cell values in the audit note
    }

    @Test
    void export_isFailClosed_whenTheAuditRowCannotBeWritten() {
        QuotationRepository repo = mock(QuotationRepository.class);
        AuditService audit = mock(AuditService.class);
        when(repo.quotationReport(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(row(1)), inv.<Pageable>getArgument(6), 1));
        doThrow(new IllegalStateException("audit down")).when(audit)
                .record(any(), any(), any(), any(), any(), any(), any());
        ReportServiceImpl service = new ReportServiceImpl(repo, new TenantResolver(), audit, 5000);
        login(DataScope.PARTNER, 12L);

        assertThrows(IllegalStateException.class, () -> service.export(new ReportFilterRequestDto(), "GST", "PDF"));
    }

    @Test
    void refusedAndInvalidExports_areNotAudited_andUnknownTypeOrFormatIsRejected() {
        QuotationRepository repo = mock(QuotationRepository.class);
        AuditService audit = mock(AuditService.class);
        ReportServiceImpl service = new ReportServiceImpl(repo, new TenantResolver(), audit, 5000);
        login(DataScope.PARTNER, 12L);

        assertEquals("VALIDATION_ERROR", assertThrows(BusinessException.class,
                () -> service.export(new ReportFilterRequestDto(), "USERS", "EXCEL")).getErrorCode());
        assertEquals("VALIDATION_ERROR", assertThrows(BusinessException.class,
                () -> service.export(new ReportFilterRequestDto(), "GST", "CSV")).getErrorCode());
        verifyNoInteractions(repo, audit);
    }

    @Test
    void quotationRows_carryTheIdSoTheUiCanOpenThem() {
        QuotationRepository repo = mock(QuotationRepository.class);
        when(repo.quotationReport(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(row(41)), inv.<Pageable>getArgument(6), 1));
        ReportServiceImpl service = new ReportServiceImpl(repo, new TenantResolver(), mock(AuditService.class), 5000);
        login(DataScope.PARTNER, 12L);

        var page = service.quotationReport(new ReportFilterRequestDto(), null);
        assertEquals(41L, page.getContent().get(0).getId());
    }
}