package com.teya.ledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.storage.type=in-memory")
class IdempotencyIT {

    @Autowired
    MockMvc mvc;

    @Test
    void deposit_with_missing_key_returns_400() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(post("/account/" + accountId + "/deposit")
                .contentType("application/json")
                .content("{\"amountMinorUnits\":100,\"currency\":\"GBP\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void deposit_replay_returns_identical_response_without_double_charging() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        // One key per test, reused within the test for the replay path. Using
        // TestSetup.key() (UUID) guarantees no collision with cached entries
        // from sibling test classes that share the Spring application context.
        String key = TestSetup.key();

        MvcResult first = mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"amountMinorUnits\":100,\"currency\":\"GBP\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String firstBody = first.getResponse().getContentAsString();

        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"amountMinorUnits\":100,\"currency\":\"GBP\"}"))
            .andExpect(status().isCreated())
            .andExpect(content().json(firstBody));

        // The account balance must be 100, not 200 (no double-charge).
        mvc.perform(get("/account/" + accountId))
            .andExpect(jsonPath("$.balanceMinorUnits").value(100));
    }

    @Test
    void deposit_same_key_different_body_returns_409() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        String key = TestSetup.key();

        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"amountMinorUnits\":100,\"currency\":\"GBP\"}"))
            .andExpect(status().isCreated());

        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"amountMinorUnits\":200,\"currency\":\"GBP\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST"));
    }
}
