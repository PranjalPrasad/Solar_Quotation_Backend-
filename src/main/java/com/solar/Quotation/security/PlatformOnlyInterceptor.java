package com.solar.Quotation.security;

import com.solar.Quotation.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Channel partner endpoints sirf Super Admin ke liye; permission galti se mil bhi jaye to 403. */
@Component
public class PlatformOnlyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PlatformOnlyInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        CurrentUser u = TenantContext.get();
        if (u == null || !u.isSuperAdmin()) {
            log.warn("[PARTNER] non-platform access denied userId={} path={}",
                    u == null ? null : u.getUserId(), req.getRequestURI());
            throw new ApiException(403, "Forbidden");
        }
        return true;
    }
}