package com.solar.Quotation.service;

import com.solar.Quotation.entity.AuditLog;
import com.solar.Quotation.repository.AuditLogRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** audit_log is insert-only. These checks keep the Java side of that promise (the DB side is V101). */
class AuditImmutabilityTest {

    @Test
    void entityIsImmutableAndStandalone() {
        assertTrue(AuditLog.class.isAnnotationPresent(Immutable.class), "AuditLog must be @Immutable");
        assertEquals(Object.class, AuditLog.class.getSuperclass(),
                "AuditLog must not extend BaseEntity/TenantBaseEntity (soft delete, version, updated_at, @PreUpdate)");
    }

    @Test
    void entityHasNoSetters() {
        List<String> setters = new ArrayList<>();
        for (Method m : AuditLog.class.getDeclaredMethods()) {
            if (m.getName().startsWith("set") && !Modifier.isPrivate(m.getModifiers())) setters.add(m.getName());
        }
        assertTrue(setters.isEmpty(), "AuditLog must have no setters: " + setters);
    }

    @Test
    void everyColumnIsNotUpdatable() {
        for (Field f : AuditLog.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Column c = f.getAnnotation(Column.class);
            assertTrue(c != null && !c.updatable(), "AuditLog." + f.getName() + " must be @Column(updatable = false)");
            if (f.isAnnotationPresent(Id.class)) continue;
        }
    }

    @Test
    void repositoryOffersNoModifyingQueries() {
        for (Method m : AuditLogRepository.class.getDeclaredMethods()) {
            assertTrue(!m.isAnnotationPresent(Modifying.class), "AuditLogRepository." + m.getName() + " must not be @Modifying");
            String n = m.getName().toLowerCase();
            assertTrue(!n.startsWith("delete") && !n.startsWith("update") && !n.startsWith("remove"), m.getName());
        }
    }

    @Test
    void nothingInMainDeletesFromTheAuditRepository() throws IOException {
        Pattern bad = Pattern.compile("(?i)audit\\w*(?:repo|repository)\\s*\\.\\s*(?:delete|deleteAll|deleteById|deleteInBatch|flush\\w*\\s*\\(\\)\\s*;\\s*\\w+\\.delete)");
        Pattern inAuditImpl = Pattern.compile("\\brepository\\s*\\.\\s*delete");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Path.of("src/main/java"))) {
            for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(p);
                if (bad.matcher(src).find()) hits.add(p.toString());
                if (p.getFileName().toString().equals("AuditServiceImpl.java") && inAuditImpl.matcher(src).find()) hits.add(p.toString());
            }
        }
        assertTrue(hits.isEmpty(), "audit rows must never be deleted from code: " + hits);
    }

    @Test
    void migrationInstallsBothTriggers() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/V101__audit_log_immutable.sql"));
        assertTrue(sql.contains("BEFORE UPDATE ON audit_log"));
        assertTrue(sql.contains("BEFORE DELETE ON audit_log"));
        assertTrue(sql.contains("previous_data") && sql.contains("new_data"));
    }
}