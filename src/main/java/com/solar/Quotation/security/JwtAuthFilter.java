package com.solar.Quotation.security;

import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.entity.ChannelPartner;
import com.solar.Quotation.entity.Role;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * L1 of tenant isolation. Validates the token, then re-checks user, role and partner in the database, so
 * deactivating a user or a partner takes effect on the very next request. Fills TenantContext.
 * Not a Spring bean on purpose: SecurityConfig adds it to the chain, so it runs exactly once.
 * PERFORMANCE: three small primary-key queries per request. If profiling ever shows this, cache user, role and
 * partner status for about 30 seconds and evict on status change.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

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
                    SecurityErrorWriter.write(objectMapper, response, 401, "TOKEN_EXPIRED", "Access token expired");
                    return;
                } catch (JwtException | IllegalArgumentException e) {
                    SecurityErrorWriter.write(objectMapper, response, 401, "UNAUTHORIZED", "Invalid token");
                    return;
                }

                Long userId;
                try {
                    userId = Long.valueOf(claims.getSubject());
                } catch (NumberFormatException e) {
                    SecurityErrorWriter.write(objectMapper, response, 401, "UNAUTHORIZED", "Invalid token");
                    return;
                }

                AppUser user = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
                if (user == null) {
                    SecurityErrorWriter.write(objectMapper, response, 401, "UNAUTHORIZED", "User no longer exists");
                    return;
                }
                if (user.getStatus() != RecordStatus.ACTIVE) {
                    SecurityErrorWriter.write(objectMapper, response, 403, "ACCOUNT_INACTIVE", "Your account is inactive");
                    return;
                }
                ChannelPartner partner = partnerRepository.findByIdAndDeletedFalse(user.getChannelPartnerId()).orElse(null);
                if (partner == null || partner.getStatus() != RecordStatus.ACTIVE) {
                    SecurityErrorWriter.write(objectMapper, response, 403, "PARTNER_INACTIVE", "Your channel partner is inactive");
                    return;
                }
                Role role = roleRepository.findByIdAndDeletedFalse(user.getRoleId()).orElse(null);
                if (role == null || role.getStatus() != RecordStatus.ACTIVE) {
                    SecurityErrorWriter.write(objectMapper, response, 403, "ROLE_INACTIVE", "Your role is not active");
                    return;
                }

                CurrentUser current = new CurrentUser(user.getId(), user.getName(), role.getId(),
                        role.getCode(), role.getDataScope(), user.getChannelPartnerId());
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
