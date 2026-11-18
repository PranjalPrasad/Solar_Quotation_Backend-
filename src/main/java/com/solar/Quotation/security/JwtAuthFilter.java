package com.solar.Quotation.security;

import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Role;
import com.solar.Quotation.logging.RequestLoggingFilter;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.RoleRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * L1 of tenant isolation. Validates the token, then re-checks user, role and partner in the database, so
 * deactivating a user or a partner takes effect on the very next request. Fills TenantContext.
 * Also enforces: access tokens issued before the user's token_valid_after (password change / revocation) are
 * rejected, and while must_change_password is set only change-password, me and logout work.
 * Not a Spring bean on purpose: SecurityConfig adds it to the chain, so it runs exactly once.
 * PERFORMANCE: three small primary-key queries per request. If profiling ever shows this, cache user, role and
 * partner status for about 30 seconds and evict on status change.
 *
 * LOGGING: puts userId / role / partnerId into the MDC. It does NOT clear them: RequestLoggingFilter (outer)
 * clears them after the access line, so that line still shows who made the request. Tokens are never logged.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ChannelPartnerRepository partnerRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, AppUserRepository userRepository, RoleRepository roleRepository,
                         ChannelPartnerRepository partnerRepository, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.partnerRepository = partnerRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/login") || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/forgot-password") || path.equals("/api/auth/reset-password");
    }

    /** While must_change_password is set, only these calls work. */
    private static boolean passwordChangeAllowed(String path) {
        return "/api/auth/change-password".equals(path) || "/api/auth/logout".equals(path) || "/api/auth/me".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                Claims claims;
                try {
                    claims = jwtUtil.parse(header.substring(7).trim());
                } catch (ExpiredJwtException e) {
                    log.debug("[AUTH] access token expired");   // normal every few minutes; the client refreshes
                    SecurityErrorWriter.write(objectMapper, response, 401, "TOKEN_EXPIRED", "Access token expired");
                    return;
                } catch (JwtException | IllegalArgumentException e) {
                    log.warn("[AUTH] invalid token rejected ({})", e.getClass().getSimpleName());
                    SecurityErrorWriter.write(objectMapper, response, 401, "UNAUTHORIZED", "Invalid token");
                    return;
                }

                Long userId;
                try {
                    userId = Long.valueOf(claims.getSubject());
                } catch (NumberFormatException e) {
                    log.warn("[AUTH] token rejected: subject is not a user id");
                    SecurityErrorWriter.write(objectMapper, response, 401, "UNAUTHORIZED", "Invalid token");
                    return;
                }
                MDC.put(RequestLoggingFilter.MDC_USER_ID, String.valueOf(userId));

                AppUser user = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
                if (user == null) {
                    log.warn("[AUTH] token rejected: user no longer exists");
                    SecurityErrorWriter.write(objectMapper, response, 401, "UNAUTHORIZED", "User no longer exists");
                    return;
                }
                MDC.put(RequestLoggingFilter.MDC_PARTNER_ID, String.valueOf(user.getChannelPartnerId()));

                // Password changed / sessions revoked after this token was issued (iat has second precision).
                if (user.getTokenValidAfter() != null && claims.getIssuedAt() != null
                        && claims.getIssuedAt().toInstant().isBefore(
                        user.getTokenValidAfter().truncatedTo(ChronoUnit.SECONDS).toInstant(ZoneOffset.UTC))) {
                    log.warn("[AUTH] token rejected: issued before the last password change / revocation");
                    SecurityErrorWriter.write(objectMapper, response, 401, "TOKEN_REVOKED",
                            "Your session was ended. Please sign in again.");
                    return;
                }

                if (user.getStatus() != RecordStatus.ACTIVE) {
                    log.warn("[AUTH] blocked: user is inactive");
                    SecurityErrorWriter.write(objectMapper, response, 403, "ACCOUNT_INACTIVE", "Your account is inactive");
                    return;
                }
                ChannelPartner partner = partnerRepository.findByIdAndDeletedFalse(user.getChannelPartnerId()).orElse(null);
                if (partner == null || partner.getStatus() != RecordStatus.ACTIVE) {
                    log.warn("[AUTH] blocked: channel partner is inactive or missing");
                    SecurityErrorWriter.write(objectMapper, response, 403, "PARTNER_INACTIVE", "Your channel partner is inactive");
                    return;
                }
                Role role = roleRepository.findByIdAndDeletedFalse(user.getRoleId()).orElse(null);
                if (role == null || role.getStatus() != RecordStatus.ACTIVE) {
                    log.warn("[AUTH] blocked: role is inactive or missing");
                    SecurityErrorWriter.write(objectMapper, response, 403, "ROLE_INACTIVE", "Your role is not active");
                    return;
                }
                MDC.put(RequestLoggingFilter.MDC_ROLE, String.valueOf(role.getCode()));

                // Only SUPER_ADMIN may have scope ALL, and it must live in the PLATFORM partner.
                if (!ScopePolicy.isAllowed(role.getCode(), role.getDataScope())) {
                    log.warn("[AUTH] blocked: role {} has data scope ALL but is not SUPER_ADMIN", role.getCode());
                    SecurityErrorWriter.write(objectMapper, response, 403, "SCOPE_NOT_ALLOWED", "Your role has an invalid data scope");
                    return;
                }
                if (role.getDataScope() == DataScope.ALL && partner.getPartnerType() != PartnerType.PLATFORM) {
                    log.warn("[AUTH] blocked: scope ALL user is not in the PLATFORM partner partnerId={}", user.getChannelPartnerId());
                    SecurityErrorWriter.write(objectMapper, response, 403, "SCOPE_NOT_ALLOWED", "Your account is not set up correctly");
                    return;
                }

                // Forced password change (first login / admin reset): nothing else works until it is done.
                if (user.isMustChangePassword() && !passwordChangeAllowed(request.getRequestURI())) {
                    log.warn("[AUTH] blocked: password change required path={}", request.getRequestURI());
                    SecurityErrorWriter.write(objectMapper, response, 403, "PASSWORD_CHANGE_REQUIRED",
                            "You must change your password before continuing");
                    return;
                }

                // ?viewPartnerId= is read ONLY on GET/HEAD. TenantResolver decides whether the caller may use it.
                boolean readRequest = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
                Long viewPartnerId = null;
                if (readRequest) {
                    String raw = request.getParameter("viewPartnerId");
                    if (raw != null && !raw.isBlank()) {
                        try {
                            viewPartnerId = Long.valueOf(raw.trim());
                        } catch (NumberFormatException e) {
                            log.warn("[TENANT] invalid viewPartnerId rejected");
                            SecurityErrorWriter.write(objectMapper, response, 400, "INVALID_PARAMETER", "Invalid viewPartnerId");
                            return;
                        }
                    }
                }

                CurrentUser current = new CurrentUser(user.getId(), user.getName(), role.getId(),
                        role.getCode(), role.getDataScope(), user.getChannelPartnerId());
                current.setReadRequest(readRequest);
                current.setViewPartnerId(viewPartnerId);
                TenantContext.set(current);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(current, null, List.of()));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}