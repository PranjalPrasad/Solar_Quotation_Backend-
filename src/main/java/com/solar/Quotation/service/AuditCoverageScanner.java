package com.solar.Quotation.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Source scanner behind AuditCoverageContractTest (plain JDK, no Spring, runs in milliseconds).
 *
 * Rule 1  A PUBLIC method of a service/scheduler class that changes data (calls save/delete/update/... on a
 *         *Repository / *Repo field, or setDeleted(true)) must reach an audit call (auditService.record / recordAs /
 *         recordEvent), directly or through other methods of the same class.
 * Rule 2  A public method that reaches a same-transaction audit call (record / recordAs) must run in a read-write
 *         transaction (@Transactional on the method or the class, readOnly != true). AuditServiceImpl.record is
 *         Propagation.MANDATORY and a read-only MySQL transaction rejects the INSERT.
 *
 * Known limits (it is a regex scan, not a compiler): an audit call on an unrelated branch satisfies Rule 1, and
 * a write done through an unusual field name is not seen. It exists to stop the common mistake, a new
 * create/update/delete method written without an audit line.
 */
final class AuditCoverageScanner {

    record Finding(String rule, String where, String message) {
        @Override public String toString() { return "[" + rule + "] " + where + ": " + message; }
    }

    private record Method(String name, String vis, String body, boolean tx, boolean readOnly) { }

    private static final Pattern WRITE = Pattern.compile(
            "\\b\\w*(?:[Rr]epo|[Rr]epository|[Rr]epositories)\\s*\\.\\s*(?:save|saveAll|saveAndFlush|delete\\w*|insert\\w*|"
                    + "update\\w*|revoke\\w*|invalidate\\w*|markUsed|remove\\w*|increment\\w*|decrement\\w*|adjust\\w*|"
                    + "clear\\w*|reset\\w*|mark\\w*|set\\w*)\\s*\\(");
    private static final Pattern SET_DELETED = Pattern.compile("\\.setDeleted\\s*\\(\\s*true\\s*\\)");
    private static final Pattern AUDIT_ANY = Pattern.compile(
            "\\b(?:auditService|audit)\\s*\\.\\s*(?:record|recordAs|recordEvent)\\s*\\(");
    private static final Pattern AUDIT_SAME_TX = Pattern.compile(
            "\\b(?:auditService|audit)\\s*\\.\\s*(?:record|recordAs)\\s*\\(");
    private static final Pattern METHOD = Pattern.compile(
            "\\n    (public |private |protected )?(?:static )?(?:final )?(?:<[^>]+> )?[\\w<>\\[\\], ?.]+ (\\w+)\\(([^)]*)\\)"
                    + "\\s*(?:throws [\\w, .]+)?\\s*\\{");
    private static final Pattern CALL = Pattern.compile("\\b(\\w+)\\s*\\(");
    private static final Pattern CLASS_TX = Pattern.compile("@Transactional([^\\n]*)\\n(?:@\\w+[^\\n]*\\n)*public class");

    private AuditCoverageScanner() { }

    /**
     * @param exemptions "SimpleClass#method" -> reason. A stale exemption (method gone, or it now audits) is a finding too.
     * @param skipClasses classes that ARE the audit writer
     */
    static List<Finding> scan(Path sourceRoot, Map<String, String> exemptions, Set<String> skipClasses) throws IOException {
        List<Finding> out = new ArrayList<>();
        Set<String> usedExemptions = new HashSet<>();
        List<Path> files = new ArrayList<>();
        for (String dir : List.of("service", "scheduler")) {
            Path d = sourceRoot.resolve(dir);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.walk(d)) {
                s.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        for (Path p : files) {
            String cls = p.getFileName().toString().replace(".java", "");
            if (skipClasses.contains(cls)) continue;
            out.addAll(scanSource(cls, Files.readString(p), exemptions, usedExemptions));
        }
        for (String key : exemptions.keySet()) {
            String cls = key.substring(0, key.indexOf('#'));
            if (!usedExemptions.contains(key) && !skipClasses.contains(cls)) {
                out.add(new Finding("STALE-EXEMPTION", key,
                        "listed as exempt but the method no longer exists, is no longer public/mutating, or now writes an audit row. Remove it from the exemption list."));
            }
        }
        return out;
    }

    static List<Finding> scanSource(String cls, String rawSource, Map<String, String> exemptions, Set<String> usedExemptions) {
        List<Finding> out = new ArrayList<>();
        String src = strip(rawSource.replace("\r\n", "\n"));
        boolean classTx = false, classReadOnly = false;
        Matcher ct = CLASS_TX.matcher(src);
        if (ct.find()) { classTx = true; classReadOnly = ct.group(1).contains("readOnly = true"); }

        Map<String, List<Method>> methods = new HashMap<>();
        Matcher m = METHOD.matcher(src);
        while (m.find()) {
            int i = m.end(), depth = 1;
            while (depth > 0 && i < src.length()) {
                char c = src.charAt(i++);
                if (c == '{') depth++; else if (c == '}') depth--;
            }
            String before = src.substring(Math.max(0, m.start() - 400), m.start());
            String[] lines = before.split("\n");
            StringBuilder ann = new StringBuilder();
            for (int k = Math.max(0, lines.length - 4); k < lines.length; k++) ann.append(lines[k]).append(' ');
            boolean methodTx = ann.indexOf("@Transactional") >= 0;
            boolean tx = methodTx || classTx;
            boolean ro = methodTx ? ann.indexOf("readOnly = true") >= 0 : classReadOnly;
            methods.computeIfAbsent(m.group(2), k -> new ArrayList<>())
                    .add(new Method(m.group(2), m.group(1) == null ? "" : m.group(1).trim(), src.substring(m.end(), i), tx, ro));
        }

        for (Map.Entry<String, List<Method>> e : methods.entrySet()) {
            for (Method pub : e.getValue()) {
                if (!pub.vis().equals("public")) continue;
                Set<String> seen = new HashSet<>();
                List<String> stack = new ArrayList<>(List.of(pub.name()));
                boolean writes = false, audits = false, auditsSameTx = false;
                while (!stack.isEmpty()) {
                    String n = stack.remove(stack.size() - 1);
                    if (!seen.add(n) || !methods.containsKey(n)) continue;
                    for (Method x : methods.get(n)) {
                        if (WRITE.matcher(x.body()).find() || SET_DELETED.matcher(x.body()).find()) writes = true;
                        if (AUDIT_ANY.matcher(x.body()).find()) audits = true;
                        if (AUDIT_SAME_TX.matcher(x.body()).find()) auditsSameTx = true;
                        Matcher c = CALL.matcher(x.body());
                        while (c.find()) if (methods.containsKey(c.group(1)) && !seen.contains(c.group(1))) stack.add(c.group(1));
                    }
                }
                String key = cls + "#" + pub.name();
                if (writes && !audits) {
                    if (exemptions.containsKey(key)) usedExemptions.add(key);
                    else out.add(new Finding("NO-AUDIT", key,
                            "changes data but never writes an audit row. Add auditService.record(...) with the before/after snapshots, "
                                    + "or (only if it is not business data) add it to EXEMPT in AuditCoverageContractTest with a reason."));
                }
                if (auditsSameTx && (!pub.tx() || pub.readOnly())) {
                    out.add(new Finding("AUDIT-TX", key,
                            "writes an audit row but is not in a read-write @Transactional. AuditService.record is Propagation.MANDATORY."));
                }
            }
        }
        return out;
    }

    private static String strip(String s) {
        String t = s.replaceAll("(?s)/\\*.*?\\*/", "");
        t = t.replaceAll("//[^\\n]*", "");
        return t.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}