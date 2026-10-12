package com.backend.eventsrus.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a request id and (if authenticated) the caller's subject into SLF4J
 * MDC before the request proceeds, cleared in finally. logback-spring.xml's
 * pattern picks these up automatically - no change needed to
 * GlobalExceptionHandler or any other existing log.error/log.warn call, they
 * all gain this context for free. Registered after JwtAuthenticationFilter
 * (see SecurityConfig) so SecurityContextHolder is already populated by the
 * time this runs.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_KEY = "user";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        MDC.put(REQUEST_ID_KEY, UUID.randomUUID().toString().substring(0, 8));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            MDC.put(USER_KEY, String.valueOf(authentication.getPrincipal()));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(USER_KEY);
        }
    }
}
