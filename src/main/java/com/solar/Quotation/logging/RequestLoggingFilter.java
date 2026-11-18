package com.solar.Quotation.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Outermost filter (runs before Spring Security, so even 401/403 get a requestId).
 *  - requestId: taken from X-Request-Id when it looks safe, otherwise a new UUID; echoed in the response.
 *  - MDC: requestId here; userId / role / partnerId are added by JwtAuthFilter once the token is verified.
 *    This filter is the only place that clears the MDC, so the final access line still carries the user.
 *  - one access line per request: METHOD path -> status (N ms). WARN when slower than 1000 ms or status 5xx.
 * The query string is never logged (it can contain search text such as phone numbers or emails).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_ROLE = "role";
    public static final String MDC_PARTNER_ID = "partnerId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$"); // blocks log injection
    private static final long SLOW_MS = 1000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = (incoming != null && SAFE_ID.matcher(incoming).matches())
                ? incoming : UUID.randomUUID().toString();

        MDC.put(MDC_REQUEST_ID, requestId);
        request.setAttribute(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);           // set first, so error responses carry it too

        long start = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = failure != null ? 500 : response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();

            if (failure != null) {
                log.error("{} {} -> {} ({} ms) unhandled in filter chain", method, path, status, ms, failure);
            } else if (status >= 500 || ms > SLOW_MS) {
                log.warn("{} {} -> {} ({} ms){}", method, path, status, ms, ms > SLOW_MS ? " SLOW" : "");
            } else if (isQuiet(method, path)) {
                log.debug("{} {} -> {} ({} ms)", method, path, status, ms);
            } else {
                log.info("{} {} -> {} ({} ms)", method, path, status, ms);
            }

            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_ROLE);
            MDC.remove(MDC_PARTNER_ID);
        }
    }

    /** Preflight, health checks and static files would drown the log. */
    private static boolean isQuiet(String method, String path) {
        return "OPTIONS".equals(method) || path.startsWith("/actuator/")
                || ("GET".equals(method) && path.startsWith("/api/files/"));   // one request per image on every page
    }
}
