package com.solar.Quotation.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAILS THE BUILD when a service method that creates, changes or deletes data has no audit call.
 *
 * You are here because this test failed. Do one of:
 *   - add  auditService.record(AuditAction.X, MODULE, entityType, id, label, tenantId, before, after)
 *     in the same method (before = snapshot taken BEFORE the change, null for create; after = null for delete); or
 *   - if the method does not change business data, add it to EXEMPT below WITH A REASON a reviewer can judge.
 */
class AuditCoverageContractTest {

    private static final Path MAIN = Path.of("src/main/java/com/solar/Quotation");

    /** The audit writer itself. */
    private static final Set<String> SKIP = Set.of("AuditServiceImpl", "AuditService");

    private static final Map<String, String> EXEMPT = Map.ofEntries(
            Map.entry("SessionRevocationService#revokeUser", "token invalidation; always called inside an audited status change (user)"),
            Map.entry("SessionRevocationService#revokePartner", "token invalidation; always called inside an audited status change (partner)"),
            Map.entry("SessionRevocationService#revokeRole", "token invalidation; always called inside an audited status change (role)"),
            Map.entry("QuotationHistoryService#recordCreated", "quotation_history ledger, written next to the audited quotation change"),
            Map.entry("QuotationHistoryService#recordUpdated", "quotation_history ledger, written next to the audited quotation change"),
            Map.entry("QuotationHistoryService#recordStatusChanged", "quotation_history ledger, written next to the audited quotation change"),
            Map.entry("QuotationHistoryService#recordConverted", "quotation_history ledger, written next to the audited quotation change"),
            Map.entry("QuotationHistoryService#recordDeleted", "quotation_history ledger, written next to the audited quotation change"),
            Map.entry("NotificationServiceImpl#markRead", "per-user inbox state, not business data"),
            Map.entry("NotificationServiceImpl#markAllRead", "per-user inbox state, not business data"),
            Map.entry("NotificationServiceImpl#delete", "per-user inbox item, not business data"),
            Map.entry("NotificationCleanupProcessor#purgePartner", "retention purge of old notifications; the job logs the counts"),
            Map.entry("LoginAttemptService#clearUser", "login throttle counter, not business data"),
            Map.entry("LoginAttemptService#registerResetRequest", "reset-request rate-limit counter, not business data"),
            Map.entry("PartnerAllowedRoleService#seedDefault", "called from the audited partner create"),
            Map.entry("SettingsServiceImpl#seedForPartner", "default settings of a new partner, created inside the audited partner create")
    );

    @Test
    void everyServiceMethodThatChangesDataWritesAnAuditRow() throws IOException {
        List<AuditCoverageScanner.Finding> findings = AuditCoverageScanner.scan(MAIN, EXEMPT, SKIP);
        assertTrue(findings.isEmpty(), () -> "Audit coverage broken:\n"
                + findings.stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    // ---- the scanner itself must keep working: these fail if someone breaks it so it reports nothing ----

    @Test
    void scannerFlagsAMethodThatSavesWithoutAnAuditCall() {
        String src = """
                @Service
                public class FakeServiceImpl {
                    private final WidgetRepository widgetRepository;
                    private final AuditService auditService;

                    @Transactional
                    public void create(Widget w) {
                        widgetRepository.save(w);
                    }
                }
                """;
        List<AuditCoverageScanner.Finding> f = AuditCoverageScanner.scanSource("FakeServiceImpl", src, Map.of(), new HashSet<>());
        assertEquals(1, f.size(), f::toString);
        assertEquals("NO-AUDIT", f.get(0).rule());
    }

    @Test
    void scannerAcceptsAnAuditCallThroughAPrivateHelper() {
        String src = """
                @Service
                public class FakeServiceImpl {
                    @Transactional
                    public void delete(Long id) {
                        doDelete(id);
                    }
                    private void doDelete(Long id) {
                        widgetRepository.deleteById(id);
                        auditService.record(AuditAction.DELETE, "X", "Widget", id, "w", 1L, before, null);
                    }
                }
                """;
        assertTrue(AuditCoverageScanner.scanSource("FakeServiceImpl", src, Map.of(), new HashSet<>()).isEmpty());
    }

    @Test
    void scannerFlagsAnAuditWriteOutsideAReadWriteTransaction() {
        String src = """
                @Service
                public class FakeServiceImpl {
                    @Transactional(readOnly = true)
                    public void export() {
                        auditService.record(AuditAction.EXPORT, "X", "Report", null, "r", 1L, null, after);
                    }
                    public void forgotTransactional() {
                        auditService.record(AuditAction.EXPORT, "X", "Report", null, "r", 1L, null, after);
                    }
                }
                """;
        List<AuditCoverageScanner.Finding> f = AuditCoverageScanner.scanSource("FakeServiceImpl", src, Map.of(), new HashSet<>());
        assertEquals(2, f.size(), f::toString);
        assertTrue(f.stream().allMatch(x -> x.rule().equals("AUDIT-TX")));
    }

    @Test
    void scannerIgnoresReadOnlyMethods() {
        String src = """
                @Service
                public class FakeServiceImpl {
                    @Transactional(readOnly = true)
                    public Widget get(Long id) {
                        return widgetRepository.findById(id).orElseThrow();
                    }
                }
                """;
        assertTrue(AuditCoverageScanner.scanSource("FakeServiceImpl", src, Map.of(), new HashSet<>()).isEmpty());
    }
}