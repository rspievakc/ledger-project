package com.teya.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sanity test that confirms the Spring application context loads.
 *
 * <p>If this test fails, every other Spring-based integration test
 * will also fail. Keep it cheap and dependency-free.
 */
@SpringBootTest
class LedgerApplicationTest {

    @Test
    void contextLoads() {
        // Intentionally empty: success is the context loading without throwing.
    }
}
