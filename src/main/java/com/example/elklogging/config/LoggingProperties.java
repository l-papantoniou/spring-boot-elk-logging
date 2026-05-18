package com.example.elklogging.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "logging.http")
public class LoggingProperties {

    /** Cap on request/response body size. Larger bodies are truncated. */
    private int maxBodySize = 10_000;

    /** URI prefixes that bypass the filter entirely. */
    private Set<String> excludedPaths = Set.of("/actuator", "/health");

    /**
     * When true, request and response bodies are included in the log event.
     * Only applies to loggable content-types (JSON, XML, text, form-encoded).
     * Keep false in production to avoid logging sensitive payloads.
     */
    private boolean logBody = false;
}
