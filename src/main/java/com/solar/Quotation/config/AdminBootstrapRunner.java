package com.solar.Quotation.config;

import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.RefreshTokenRepository;
import com.solar.Quotation.security.PasswordPolicy;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

/**
 * Creates the FIRST Super Admin from the environment. data.sql no longer contains any user.
 *   BOOTSTRAP_ADMIN_EMAIL (required), BOOTSTRAP_ADMIN_PASSWORD (required, >= 12 chars, not weak),
 *   BOOTSTRAP_ADMIN_USERNAME (default "admin"), BOOTSTRAP_ADMIN_NAME (default "Super Admin").
 * Does nothing when a Super Admin already exists. The account is created with must_change_password = true.
 * The password is never logged. Remove the BOOTSTRAP_ADMIN_* variables after the first start.
 * Runs after data.sql (roles and the PLATFORM partner come from there).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    /** The password the old data.sql shipped with. Any Super Admin still using it is treated as compromised. */
    private static final String LEGACY_DEFAULT_PASSWORD = "admin@123";

    private final EntityManager em;
    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment env;
    private final TransactionTemplate tx;

    public AdminBootstrapRunner(EntityManager em, AppUserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                                Environment env, TransactionTemplate tx) {
        this.em = em;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.env = env;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> bootstrap());
    }

    void bootstrap() {
        neutralizeLegacyDefaultPassword();

        boolean provided = notBlank(env.getProperty("BOOTSTRAP_ADMIN_EMAIL")) || notBlank(env.getProperty("BOOTSTRAP_ADMIN_PASSWORD"));
        if (userRepository.countByRoleCode("SUPER_ADMIN", null, null) > 0) {
            if (provided) log.info("[AUTH] bootstrap skipped: a Super Admin already exists (BOOTSTRAP_ADMIN_* ignored, remove them)");
            return;
        }

        String email = trim(env.getProperty("BOOTSTRAP_ADMIN_EMAIL"));
        String password = env.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
        if (!notBlank(email) || !notBlank(password)) {
            String msg = "No Super Admin exists and BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD are not set";
            if (env.acceptsProfiles(Profiles.of("prod", "production"))) {
                throw new IllegalStateException("[AUTH] " + msg + ". Nobody could log in.");
            }
            log.warn("[AUTH] {}. Nobody can log in until you set them.", msg);
            return;
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalStateException("[AUTH] BOOTSTRAP_ADMIN_EMAIL is not a valid e-mail address");
        }
        String username = notBlank(env.getProperty("BOOTSTRAP_ADMIN_USERNAME")) ? env.getProperty("BOOTSTRAP_ADMIN_USERNAME").trim() : "admin";
        String name = notBlank(env.getProperty("BOOTSTRAP_ADMIN_NAME")) ? env.getProperty("BOOTSTRAP_ADMIN_NAME").trim() : "Super Admin";

        String weak = PasswordPolicy.weakness(password, PasswordPolicy.MIN_BOOTSTRAP, username, PasswordPolicy.localPart(email));
        if (weak != null) {
            throw new IllegalStateException("[AUTH] BOOTSTRAP_ADMIN_PASSWORD rejected: " + weak
                    + " (bootstrap needs at least " + PasswordPolicy.MIN_BOOTSTRAP + " characters)");
        }
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("[AUTH] bootstrap username or e-mail is already used by a non Super Admin user");
        }

        Role role = first(em.createQuery("select r from Role r where r.code = :c and r.deleted = false", Role.class)
                .setParameter("c", "SUPER_ADMIN").getResultList());
        ChannelPartner platform = first(em.createQuery("select p from ChannelPartner p where p.code = :c and p.deleted = false",
                ChannelPartner.class).setParameter("c", "PLATFORM").getResultList());
        if (role == null || platform == null) {
            throw new IllegalStateException("[AUTH] SUPER_ADMIN role or PLATFORM partner missing: data.sql did not run");
        }

        AppUser u = new AppUser();
        u.setCode(nextCode());
        u.setName(name);
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setStatus(RecordStatus.ACTIVE);
        u.setRoleId(role.getId());
        u.setChannelPartnerId(platform.getId());
        u.setMustChangePassword(true);
        userRepository.save(u);

        log.warn("[AUTH] bootstrap: first Super Admin created username={} (must change password at first login). "
                + "Remove BOOTSTRAP_ADMIN_* from the environment now.", username);
    }

    /** Databases seeded by the old data.sql: a Super Admin that still has the published password gets an unusable one. */
    private void neutralizeLegacyDefaultPassword() {
        List<AppUser> admins = em.createQuery("select u from AppUser u, Role r where u.roleId = r.id "
                + "and r.code = 'SUPER_ADMIN' and u.deleted = false", AppUser.class).getResultList();
        for (AppUser u : admins) {
            if (u.getPasswordHash() != null && passwordEncoder.matches(LEGACY_DEFAULT_PASSWORD, u.getPasswordHash())) {
                byte[] junk = new byte[48];
                new SecureRandom().nextBytes(junk);
                u.setPasswordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(junk)));
                u.setMustChangePassword(true);
                u.setTokenValidAfter(LocalDateTime.now(ZoneOffset.UTC));
                userRepository.save(u);
                refreshTokenRepository.revokeAllForUser(u.getId());
                log.error("[AUTH] Super Admin userId={} still had the published default password: password invalidated. "
                        + "Use 'Forgot password' to set a new one (or delete the row and set BOOTSTRAP_ADMIN_*).", u.getId());
            }
        }
    }

    private String nextCode() {
        long start = userRepository.count() + 1;
        for (long i = start; i < start + 500; i++) {
            String c = String.format("USR-%04d", i);
            Long n = em.createQuery("select count(u) from AppUser u where u.code = :c", Long.class)
                    .setParameter("c", c).getSingleResult();
            if (n == 0) return c;
        }
        return "USR-" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    private static <T> T first(List<T> l) { return l.isEmpty() ? null : l.get(0); }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String trim(String s) { return s == null ? null : s.trim(); }
}
