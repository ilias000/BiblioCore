package com.iliaspiotopoulos.bibliocore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("BiblioCore Library Management API")
                        .version("1.0.0")
                        .description("""
                                A RESTful API for managing a public library network.

                                Features:
                                - Book catalog management with search and pagination
                                - Member registration and management
                                - Loan lifecycle with overdue detection and fines
                                - Waitlist/reservation system for unavailable books
                                - Audit trail for all state changes

                                Authentication: JWT Bearer token
                                """)
                        .contact(new Contact()
                                .name("BiblioCore Team")
                                .email("il.piotopoulos@gmailcom"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtained from /api/v1/auth/login")));
    }
}