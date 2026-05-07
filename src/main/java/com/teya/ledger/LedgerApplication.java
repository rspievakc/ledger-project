package com.teya.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Teya ledger HTTP service.
 *
 * <p>Boots Spring Boot's embedded web server and wires up the
 * controllers, services, and storage adapters defined under
 * {@code com.teya.ledger.*}.
 */
@SpringBootApplication
public class LedgerApplication {

    /**
     * Standard Java {@code main} entry point.
     *
     * @param args command-line arguments forwarded to Spring Boot.
     */
    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
