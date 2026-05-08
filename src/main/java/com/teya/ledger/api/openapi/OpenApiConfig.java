package com.teya.ledger.api.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customises the springdoc-generated OpenAPI document with project
 * metadata (title, description, version, contact, license).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Teya Ledger API")
            .description("Event-sourced money ledger: customers, accounts, "
                + "deposits, withdrawals, transaction history. "
                + "All write endpoints require an Idempotency-Key header.")
            .version("1.0.0")
            .contact(new Contact().name("Teya"))
            .license(new License().name("MIT")));
    }
}
