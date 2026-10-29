package com.solar.Quotation.service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.solar.Quotation.dto.requestdto.QuotationHistoryDto;
import com.solar.Quotation.entity.QuotationHistory;
import com.solar.Quotation.repository.QuotationHistoryRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class QuotationHistoryService {

    private static final Set<String> IGNORED = Set.of(
            "id", "code", "quoteDate", "createdAt", "updatedAt", "createdBy", "updatedBy", "version",
            "customer.id", "partner", "partnerId",
            "subtotal", "subTotal", "discountAmount", "sgst", "cgst", "sgstAmount", "cgstAmount", "itemsTotal");

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final TypeReference<List<QuotationHistoryDto.Change>> CHANGES = new TypeReference<>() { };

    private final QuotationHistoryRepository repo;
    private final ObjectMapper mapper;

    public QuotationHistoryService(QuotationHistoryRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ write side

    @Transactional
    public void recordCreated(Long quotationId, String code, Object after) {
        save(quotationId, code, "CREATED", "Quotation created", after, List.of());
    }

    /** Skips the row when nothing actually changed (a save with identical values). */
    @Transactional
    public void recordUpdated(Long quotationId, String code, Object before, Object after) {
        List<QuotationHistoryDto.Change> changes = diff(before, after);
        if (changes.isEmpty()) return;
        String fields = String.join(", ", changes.stream().limit(4).map(c -> c.field()).toList());
        String more = changes.size() > 4 ? " +" + (changes.size() - 4) + " more" : "";
        save(quotationId, code, "UPDATED", changes.size() + " field(s) changed: " + fields + more, after, changes);
    }

    @Transactional
    public void recordStatusChanged(Long quotationId, String code, String oldStatus, String newStatus, Object after) {
        if (Objects.equals(oldStatus, newStatus)) return;
        save(quotationId, code, "STATUS_CHANGED", "Status changed from " + oldStatus + " to " + newStatus, after,
                List.of(new QuotationHistoryDto.Change("status", oldStatus, newStatus)));
    }

    /** Optional: call when a quotation is converted to an order. */
    @Transactional
    public void recordConverted(Long quotationId, String code, String orderReference, Object after) {
        save(quotationId, code, "CONVERTED", "Converted to order " + orderReference, after, List.of());
    }

    /** Call BEFORE deleting the quotation. The history rows stay (there is no foreign key). */
    @Transactional
    public void recordDeleted(Long quotationId, String code, Object before) {
        save(quotationId, code, "DELETED", "Quotation deleted", before, List.of());
    }

    private void save(Long quotationId, String code, String action, String summary, Object snapshotSource,
                      List<QuotationHistoryDto.Change> changes) {
        Map<String, Object> snap = toMap(snapshotSource);
        Actor who = currentActor();

        QuotationHistory h = new QuotationHistory();
        h.setQuotationId(quotationId);
        h.setQuotationCode(code);
        h.setVersion(repo.maxVersion(quotationId) + 1);
        h.setAction(action);
        h.setSummary(summary);
        h.setStatus(snap.get("status") == null ? null : String.valueOf(snap.get("status")));
        h.setTotalAmount(toDecimal(snap.get("totalAmount")));
        h.setChangedById(who.id());
        h.setChangedByName(who.name());
        h.setChangedByRole(who.role());
        h.setChangedAt(Instant.now());
        h.setChangesJson(json(changes));
        h.setSnapshotJson(json(snap));
        repo.save(h);
    }

    // ------------------------------------------------------------------ read side

    @Transactional(readOnly = true)
    public List<QuotationHistoryDto> list(Long quotationId) {
        // Only Super Admin sees changes made by Super Admin; partner users see their own partner's changes only.
        com.solar.Quotation.security.CurrentUser viewer = com.solar.Quotation.security.TenantContext.get();
        boolean hideAdmin = viewer == null || !viewer.isSuperAdmin();
        return repo.findByQuotationIdOrderByVersionDesc(quotationId).stream()
                .filter(h -> !hideAdmin || !"SUPER_ADMIN".equalsIgnoreCase(h.getChangedByRole()))
                .map(h -> new QuotationHistoryDto(
                        h.getId(), h.getVersion(), h.getAction(), h.getSummary(),
                        new QuotationHistoryDto.ChangedBy(h.getChangedById(), h.getChangedByName(), h.getChangedByRole()),
                        h.getChangedAt(), h.getStatus(), h.getTotalAmount(), parseChanges(h.getChangesJson()))).toList();
    }

    // ------------------------------------------------------------------ diff

    List<QuotationHistoryDto.Change> diff(Object before, Object after) {
        Map<String, String> a = flatten(toMap(before));
        Map<String, String> b = flatten(toMap(after));
        Set<String> keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        List<QuotationHistoryDto.Change> out = new ArrayList<>();
        for (String k : keys) {
            String o = a.get(k), n = b.get(k);
            if (!same(o, n)) out.add(new QuotationHistoryDto.Change(k, o, n));
        }
        return out;
    }

    private static boolean same(String a, String b) {
        if (a == null || a.isBlank()) return b == null || b.isBlank();
        if (b == null) return false;
        BigDecimal x = toDecimal(a), y = toDecimal(b);
        return (x != null && y != null) ? x.compareTo(y) == 0 : a.equals(b);
    }

    private Map<String, String> flatten(Map<String, Object> m) {
        Map<String, String> out = new LinkedHashMap<>();
        flatten("", m, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> m, Map<String, String> out) {
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (IGNORED.contains(path)) continue;
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                flatten(path, (Map<String, Object>) nested, out);
            } else if (v instanceof List<?> list) {
                out.put(path, "items".equals(path) ? itemsText(list) : json(list));
            } else {
                out.put(path, v == null ? null : plain(v));
            }
        }
    }

    /** "Solar Panels x 3300 W @ 22; Inverter x 3 kW @ 6500" — readable and stable for comparison. */
    @SuppressWarnings("unchecked")
    private static String itemsText(List<?> items) {
        StringJoiner sj = new StringJoiner("; ");
        for (Object o : items) {
            if (!(o instanceof Map<?, ?>)) continue;
            Map<String, Object> it = (Map<String, Object>) o;
            sj.add(it.get("name") + " x " + plain(it.get("qty")) + " " + (it.get("unit") == null ? "" : it.get("unit"))
                    + " @ " + plain(it.get("rate")));
        }
        return sj.toString();
    }

    private static String plain(Object v) {
        if (v instanceof Number) {
            try { return new BigDecimal(String.valueOf(v)).stripTrailingZeros().toPlainString(); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return String.valueOf(v);
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(String.valueOf(v).trim()); } catch (NumberFormatException e) { return null; }
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> toMap(Object o) {
        if (o == null) return new LinkedHashMap<>();
        return mapper.convertValue(o, MAP);
    }

    private String json(Object o) {
        return mapper.writeValueAsString(o);
    }

    private List<QuotationHistoryDto.Change> parseChanges(String s) {
        if (s == null || s.isBlank()) return List.of();
        try {
            return mapper.readValue(s, CHANGES);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private record Actor(Long id, String name, String role) { }

    /**
     * Who made the change. Uses the Spring Security principal's name and role. If your principal is your own
     * user object (with an id and display name), read those from it here instead.
     */
    private Actor currentActor() {
        // The login filter puts a CurrentUser in TenantContext (the Spring authorities list is empty),
        // so read id, name and role code from there.
        com.solar.Quotation.security.CurrentUser u = com.solar.Quotation.security.TenantContext.get();
        if (u == null) return new Actor(null, "System", null);
        return new Actor(u.getUserId(), u.getName(), u.getRoleCode());
    }
}