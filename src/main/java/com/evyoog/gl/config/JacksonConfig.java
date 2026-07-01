package com.evyoog.gl.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's auto-configured ObjectMapper is Jackson 3 (tools.jackson.databind).
 * AuditService needs a classic Jackson 2 (com.fasterxml.jackson.databind) ObjectMapper
 * to build the JSON snapshots stored in gl.audit_log — this is unrelated to, and
 * does not replace, Spring MVC's own request/response JSON conversion.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper auditObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }
}
