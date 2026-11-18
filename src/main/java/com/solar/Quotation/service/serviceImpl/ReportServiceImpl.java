package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.dto.requestdto.ReportFilterRequestDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.ReportRowResponseDto;
import com.solar.Quotation.dto.responsedto.ReportSummaryResponseDto;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.ReportService;
import com.solar.Quotation.util.ExcelExportUtil;
import com.solar.Quotation.util.PdfExportUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    /** Largest page any report call returns; also the batch the export reads with. */
    static final int MAX_PAGE_SIZE = 500;
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "quoteDate", "totalAmount", "code", "status", "systemSizeKw");

    private final QuotationRepository quotationRepo;
    private final TenantResolver tenantResolver;
    private final AuditService auditService;
    private final int maxExportRows;

    public ReportServiceImpl(QuotationRepository quotationRepo, TenantResolver tenantResolver,
                             AuditService auditService,
                             @Value("${app.export.max-rows:5000}") int maxExportRows) {
        this.quotationRepo = quotationRepo;
        this.tenantResolver = tenantResolver;
        this.auditService = auditService;
        this.maxExportRows = Math.min(Math.max(maxExportRows, 1), ExcelExportUtil.MAX_ROWS);
    }

    /**
     * Every report method is a GET: own tenant, or the partner named by ?viewPartnerId= for Super Admin.
     * A partnerId in the filter is ignored (WARN), it can never widen access.
     */
    private Long partner(ReportFilterRequestDto f) {
        if (f.getPartnerId() != null && !f.getPartnerId().equals(tenantResolver.ownPartnerId())) {
            log.warn("[REPORT] filter partnerId ignored, use viewPartnerId requestedPartnerId={} {}", f.getPartnerId(), Actors.describe());
        }
        return tenantResolver.effectiveReadPartnerId();
    }
    private Long owner() { return tenantResolver.ownerFilter(); }

    private QuotationStatus status(String s) {
        if (s == null || s.isBlank()) return null;
        try { return QuotationStatus.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw new BusinessException("VALIDATION_ERROR", "Unknown status " + s);
        }
    }

    private Pageable pageable(ReportFilterRequestDto f) {
        String[] p = f.getSort() == null || f.getSort().isBlank()
                ? new String[]{"createdAt", "desc"} : f.getSort().split(",");
        String field = SORT_FIELDS.contains(p[0].trim()) ? p[0].trim() : "createdAt";
        Sort.Direction dir = p.length > 1 && "asc".equalsIgnoreCase(p[1].trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int size = Math.min(Math.max(f.getSize(), 1), MAX_PAGE_SIZE);
        // id as the tie-breaker: pages never skip or repeat a row when many quotations share one created_at
        return PageRequest.of(Math.max(f.getPage(), 0), size, Sort.by(dir, field).and(Sort.by(dir, "id")));
    }

    // ------------------------------------------------------------------ logging helpers ([REPORT], Module 0)

    private static long ms(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000L; }

    /** Status comes from the client: keep letters/digits/underscore only so it can never forge a log line. */
    private static String safe(String s) {
        if (s == null || s.isBlank()) return "-";
        String t = s.trim().replaceAll("[^A-Za-z0-9_]", "_");
        return t.substring(0, Math.min(t.length(), 20));
    }

    /** Filters only: dates, status, ids. Never a name, e-mail, phone or any cell value. */
    private static String filters(ReportFilterRequestDto f, Long partnerId) {
        return "from=" + f.getFrom() + " to=" + f.getTo() + " status=" + safe(f.getStatus())
                + " salesUserId=" + f.getSalesUserId() + " partnerId=" + partnerId;
    }

    /** One timed, logged read. partnerId is resolved once (own tenant, or the Super Admin's viewPartnerId). */
    private <T> T read(String op, ReportFilterRequestDto f, Function<Long, T> body, ToLongFunction<T> rows) {
        long started = System.nanoTime();
        Long partnerId = partner(f);
        try {
            T result = body.apply(partnerId);
            log.info("[REPORT] {} {} page={} size={} rows={} durationMs={} {}", op, filters(f, partnerId),
                    f.getPage(), f.getSize(), rows.applyAsLong(result), ms(started), Actors.describe());
            return result;
        } catch (BusinessException ex) {
            log.warn("[REPORT] {} rejected code={} {} durationMs={} {}", op, ex.getErrorCode(),
                    filters(f, partnerId), ms(started), Actors.describe());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("[REPORT] {} failed {} durationMs={} {}", op, filters(f, partnerId), ms(started),
                    Actors.describe(), ex);
            throw ex;
        }
    }

    // ------------------------------------------------------------------ reads

    @Override
    public ReportSummaryResponseDto getSummary(ReportFilterRequestDto f) {
        return read("summary", f, pid -> {
            Object[] r = quotationRepo.reportSummary(
                    pid, owner(), status(f.getStatus()), f.getSalesUserId(), f.getFrom(), f.getTo());
            ReportSummaryResponseDto d = new ReportSummaryResponseDto();
            if (r != null) {
                d.setTotalQuotations(r[0] == null ? 0L : ((Number) r[0]).longValue());
                d.setAccepted(r[1] == null ? 0L : ((Number) r[1]).longValue());
                d.setPending(r[2] == null ? 0L : ((Number) r[2]).longValue());
                d.setRejected(r[3] == null ? 0L : ((Number) r[3]).longValue());
                d.setTotalValue(r[4] == null ? BigDecimal.ZERO : (BigDecimal) r[4]);
                d.setTotalGst(r[5] == null ? BigDecimal.ZERO : (BigDecimal) r[5]);
                d.setTotalReferralPayable(r[6] == null ? BigDecimal.ZERO : (BigDecimal) r[6]);
            }
            return d;
        }, d -> d.getTotalQuotations());
    }

    @Override
    public PageResponse<ReportRowResponseDto.Quotation> quotationReport(ReportFilterRequestDto f, Pageable ignored) {
        return read("quotations", f, pid -> queryQuotations(f, pid), r -> r.getContent().size());
    }

    @Override
    public PageResponse<ReportRowResponseDto.Gst> gstReport(ReportFilterRequestDto f, Pageable ignored) {
        return read("gst", f, pid -> queryGst(f, pid), r -> r.getContent().size());
    }

    @Override
    public PageResponse<ReportRowResponseDto.ReferralIncome> referralIncomeReport(ReportFilterRequestDto f, Pageable ignored) {
        return read("referral-income", f, pid -> queryReferral(f, pid), r -> r.getContent().size());
    }

    @Override
    public PageResponse<ReportRowResponseDto.Performer> topPerformers(ReportFilterRequestDto f, Pageable ignored) {
        return read("top-performers", f, pid -> {
            Page<Object[]> page = quotationRepo.topPerformers(
                    pid, owner(), f.getFrom(), f.getTo(),
                    PageRequest.of(Math.max(f.getPage(), 0), Math.min(Math.max(f.getSize(), 1), MAX_PAGE_SIZE)));
            return PageResponse.of(page.map(r -> {
                ReportRowResponseDto.Performer d = new ReportRowResponseDto.Performer();
                d.setUserId(r[0] == null ? null : ((Number) r[0]).longValue());
                d.setName((String) r[1]);
                d.setQuotes(r[2] == null ? 0L : ((Number) r[2]).longValue());
                d.setAccepted(r[3] == null ? 0L : ((Number) r[3]).longValue());
                d.setRatePercent(d.getQuotes() > 0
                        ? BigDecimal.valueOf(d.getAccepted() * 100.0 / d.getQuotes()).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
                return d;
            }));
        }, r -> r.getContent().size());
    }

    // The query methods below do the work without logging, so an export that reads many batches logs ONE line.

    private PageResponse<ReportRowResponseDto.Quotation> queryQuotations(ReportFilterRequestDto f, Long partnerId) {
        Page<Object[]> page = quotationRepo.quotationReport(
                partnerId, owner(), status(f.getStatus()), f.getSalesUserId(), f.getFrom(), f.getTo(), pageable(f));
        return PageResponse.of(page.map(r -> {
            ReportRowResponseDto.Quotation d = new ReportRowResponseDto.Quotation();
            d.setQuoteNo((String) r[0]);
            d.setCustomer((String) r[1]);
            d.setSystemSizeKw((BigDecimal) r[2]);
            d.setAmount((BigDecimal) r[3]);
            d.setSalesPerson((String) r[4]);
            d.setStatus(r[5] == null ? null : r[5].toString());
            d.setDate(toLocalDate(r[6]));
            d.setPartnerName((String) r[7]);
            d.setId(r.length > 8 && r[8] != null ? ((Number) r[8]).longValue() : null);   // lets the UI open / download the quotation
            return d;
        }));
    }

    private PageResponse<ReportRowResponseDto.Gst> queryGst(ReportFilterRequestDto f, Long partnerId) {
        Page<Object[]> page = quotationRepo.gstReport(
                partnerId, owner(), status(f.getStatus()), f.getSalesUserId(), f.getFrom(), f.getTo(), pageable(f));
        return PageResponse.of(page.map(r -> {
            ReportRowResponseDto.Gst d = new ReportRowResponseDto.Gst();
            d.setQuoteNo((String) r[0]);
            d.setCustomer((String) r[1]);
            d.setTaxableAmount((BigDecimal) r[2]);
            d.setGstPercent((BigDecimal) r[3]);
            d.setCgstAmount((BigDecimal) r[4]);
            d.setSgstAmount((BigDecimal) r[5]);
            d.setGstAmount((BigDecimal) r[6]);
            d.setTotal((BigDecimal) r[7]);
            d.setDate(toLocalDate(r[8]));
            return d;
        }));
    }

    private PageResponse<ReportRowResponseDto.ReferralIncome> queryReferral(ReportFilterRequestDto f, Long partnerId) {
        Page<Object[]> page = quotationRepo.referralIncomeReport(
                partnerId, owner(), status(f.getStatus()), f.getSalesUserId(), f.getFrom(), f.getTo(), pageable(f));
        return PageResponse.of(page.map(r -> {
            ReportRowResponseDto.ReferralIncome d = new ReportRowResponseDto.ReferralIncome();
            d.setReferrerName((String) r[0]);
            d.setReferredCustomer((String) r[1]);
            d.setQuotationAmount((BigDecimal) r[2]);
            d.setReferralPercent((BigDecimal) r[3]);
            d.setReferralAmount((BigDecimal) r[4]);
            d.setStatus(r[5] == null ? null : r[5].toString());
            return d;
        }));
    }

    // ------------------------------------------------------------------ export

    private static String normalizeFormat(String format) {
        String t = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);
        if (t.equals("EXCEL") || t.equals("PDF")) return t;
        throw new BusinessException("VALIDATION_ERROR", "format must be EXCEL or PDF");
    }

    private static String normalizeKind(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "QUOTATION", "QUOTATIONS" -> "QUOTATIONS";
            case "GST" -> "GST";
            case "REFERRAL", "REFERRAL_INCOME" -> "REFERRAL_INCOME";
            default -> throw new BusinessException("VALIDATION_ERROR", "Unknown report type");
        };
    }

    /**
     * Tenant-scoped (own tenant, or the Super Admin's viewPartnerId, via partner(f)). Read in batches of MAX_PAGE_SIZE
     * and refused above app.export.max-rows instead of silently cutting the report. Text cells are formula-escaped in
     * ExcelExportUtil (SpreadsheetSafe). Every successful export writes an audit row (who, what, filters, rows) in the
     * same writable transaction BEFORE the file is returned: if the audit cannot be written, nothing is handed out.
     */
    @Override
    @Transactional
    public byte[] export(ReportFilterRequestDto f, String type, String format) {
        long started = System.nanoTime();
        String fmt = normalizeFormat(format);
        String kind = normalizeKind(type);
        boolean excel = "EXCEL".equals(fmt);
        Long partnerId = partner(f);
        int rows = 0;
        try {
            byte[] body;
            if ("GST".equals(kind)) {
                List<ReportRowResponseDto.Gst> list = collectAll(f, kind, partnerId, x -> queryGst(x, partnerId));
                rows = list.size();
                body = excel ? ExcelExportUtil.gst(list) : PdfExportUtil.gst(list);
            } else if ("REFERRAL_INCOME".equals(kind)) {
                List<ReportRowResponseDto.ReferralIncome> list = collectAll(f, kind, partnerId, x -> queryReferral(x, partnerId));
                rows = list.size();
                body = excel ? ExcelExportUtil.referral(list) : PdfExportUtil.referral(list);
            } else {
                List<ReportRowResponseDto.Quotation> list = collectAll(f, kind, partnerId, x -> queryQuotations(x, partnerId));
                rows = list.size();
                body = excel ? ExcelExportUtil.quotations(list) : PdfExportUtil.quotations(list);
            }

            java.util.Map<String, Object> exported = new java.util.LinkedHashMap<>();   // what was exported, never the data
            exported.put("type", kind);
            exported.put("format", fmt);
            exported.put("rows", rows);
            exported.put("filters", filters(f, partnerId));
            auditService.record(AuditAction.EXPORT, "REPORT", "Report", null, kind + " " + fmt, partnerId, null, exported);

            log.info("[REPORT] export type={} format={} {} rows={} bytes={} durationMs={} {}",
                    kind, fmt, filters(f, partnerId), rows, body.length, ms(started), Actors.describe());
            return body;
        } catch (BusinessException ex) {
            throw ex;                                                   // already logged where it was raised
        } catch (RuntimeException ex) {
            log.error("[REPORT] export failed type={} format={} {} rowsRead={} durationMs={} {}",
                    kind, fmt, filters(f, partnerId), rows, ms(started), Actors.describe(), ex);
            throw ex;
        }
    }

    /** Reads every page of one report (batch MAX_PAGE_SIZE) and refuses when the total is above the export cap. */
    private <T> List<T> collectAll(ReportFilterRequestDto f, String kind, Long partnerId,
                                   Function<ReportFilterRequestDto, PageResponse<T>> page) {
        List<T> all = new ArrayList<>();
        for (int p = 0; ; p++) {
            PageResponse<T> r = page.apply(pageOf(f, p));
            if (p == 0 && r.getTotalElements() > maxExportRows) {
                log.warn("[REPORT] export refused, too many rows type={} total={} max={} {} {}",
                        kind, r.getTotalElements(), maxExportRows, filters(f, partnerId), Actors.describe());
                throw new BusinessException("EXPORT_TOO_LARGE", "This export has " + r.getTotalElements()
                        + " rows; the limit is " + maxExportRows + ". Narrow the date range or status and try again.");
            }
            all.addAll(r.getContent());
            if (r.getContent().size() < MAX_PAGE_SIZE || all.size() >= r.getTotalElements()) {
                return all;
            }
        }
    }

    private ReportFilterRequestDto pageOf(ReportFilterRequestDto f, int pageNo) {
        ReportFilterRequestDto c = new ReportFilterRequestDto();
        c.setFrom(f.getFrom());
        c.setTo(f.getTo());
        c.setStatus(f.getStatus());
        c.setSalesUserId(f.getSalesUserId());
        c.setPartnerId(f.getPartnerId());
        c.setSort(f.getSort());
        c.setPage(pageNo);
        c.setSize(MAX_PAGE_SIZE);
        return c;
    }

    private LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        return null;
    }
}