package com.example.cns.config;

import jakarta.servlet.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;

@Component
public class LoggingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // Generate a unique ID (substring(0,8) makes it short and readable)
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            // Put it in the "Context" so Logback can find it
            MDC.put("traceid", traceId);
            chain.doFilter(request, response);
        } finally {
            // Clean up after the request finishes to prevent data leaking to other requests
            MDC.remove("traceid");
        }
    }
}