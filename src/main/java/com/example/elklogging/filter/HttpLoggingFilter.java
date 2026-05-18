package com.example.elklogging.filter;

import com.example.elklogging.config.LoggingProperties;
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
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.marker.Markers.appendEntries;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    private static final Set<String> LOGGABLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/xml",
            "application/x-www-form-urlencoded"
    );

    // MDC keys stay simple and human-readable.
    // Logstash will rename them to ECS field names (trace.id, labels.correlation_id) downstream.
    private static final String MDC_TRACE_ID       = "traceId";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final LoggingProperties props;

    public HttpLoggingFilter(LoggingProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return props.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var wrappedRequest  = new ContentCachingRequestWrapper(request, props.getMaxBodySize());
        var wrappedResponse = new ContentCachingResponseWrapper(response);

        // Extract or generate distributed trace IDs
        String traceId = Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        // Store in MDC — every log line emitted during this request
        // (by any class, not just this filter) will carry these IDs
        MDC.put(MDC_TRACE_ID,       traceId);
        MDC.put(MDC_CORRELATION_ID, correlationId);

        // Propagate IDs back to the caller via response headers
        wrappedResponse.addHeader("X-Trace-Id",       traceId);
        wrappedResponse.addHeader("X-Correlation-Id", correlationId);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;

            // Log BEFORE copyBodyToResponse — the buffer is still intact here
            logExchange(wrappedRequest, wrappedResponse, durationMs);

            // Flush the cached response bytes back to the actual output stream
            wrappedResponse.copyBodyToResponse();

            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private void logExchange(ContentCachingRequestWrapper  request,
                             ContentCachingResponseWrapper response,
                             long                          durationMs) {

        Map<String, Object> fields = new LinkedHashMap<>();
        // Field names below are deliberately close to ECS but kept simple
        // for Java readability — Logstash will finalise the rename.
        fields.put("event.kind",       "event");
        fields.put("event.category",   "web");
        fields.put("event.action",     "http-request");
        fields.put("http.method",      request.getMethod());
        fields.put("http.url",         request.getRequestURI());
        fields.put("http.query",       request.getQueryString() != null ? request.getQueryString() : "");
        fields.put("http.status_code", response.getStatus());
        // ECS expects event.duration in nanoseconds — convert from ms here.
        fields.put("event.duration",   durationMs * 1_000_000L);
        fields.put("client.ip",        resolveClientIp(request));

        extractRequestHeaders(request)
                .forEach((k, v) -> fields.put("http.request.headers."  + k, v));
        extractResponseHeaders(response)
                .forEach((k, v) -> fields.put("http.response.headers." + k, v));

        if (props.isLogBody()) {
            addBodies(request, response, fields);
        }

        log.info(appendEntries(fields),
                "HTTP {} {} -> {} ({}ms)",
                request.getMethod(), request.getRequestURI(),
                response.getStatus(), durationMs);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    private Map<String, String> extractRequestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headers.put(name, String.join(", ", java.util.Collections.list(request.getHeaders(name))));
            }
        }
        return headers;
    }

    private Map<String, String> extractResponseHeaders(ContentCachingResponseWrapper response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, String.join(", ", response.getHeaders(name)));
        }
        return headers;
    }

    private void addBodies(ContentCachingRequestWrapper  request,
                           ContentCachingResponseWrapper response,
                           Map<String, Object>           fields) {

        if (isLoggable(request.getContentType())) {
            byte[] body = request.getContentAsByteArray();
            if (body.length > 0) {
                fields.put("http.request.body", new String(body, StandardCharsets.UTF_8));
            }
        }
        if (isLoggable(response.getContentType())) {
            byte[] body = response.getContentAsByteArray();
            if (body.length > 0) {
                fields.put("http.response.body", new String(body, StandardCharsets.UTF_8));
            }
        }
    }

    private boolean isLoggable(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return LOGGABLE_CONTENT_TYPES.stream().anyMatch(lower::contains);
    }
}
