package com.evyoog.gl.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI evyoogGlOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("eVyoog GL Module API")
                        .description("General Ledger capabilities for eVyoog ERP — PLATFORM_FOUNDATION_v2.0")
                        .version("1.0.0"));
    }
}
