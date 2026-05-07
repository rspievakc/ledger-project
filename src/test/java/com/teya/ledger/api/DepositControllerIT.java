package com.teya.ledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.storage.type=in-memory")
class DepositControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void deposit_creates_event_and_returns_balance() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", "k1")
                .contentType("application/json")
                .content("{\"amountMinorUnits\":500,\"currency\":\"GBP\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.balanceAfterMinorUnits").value(500))
            .andExpect(jsonPath("$.eventId").exists())
            .andExpect(jsonPath("$.seq").isNumber());
    }

    @Test
    void deposit_rejects_missing_idempotency_key() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(post("/account/" + accountId + "/deposit")
                .contentType("application/json")
                .content("{\"amountMinorUnits\":500,\"currency\":\"GBP\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void deposit_rejects_currency_mismatch() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", "k1")
                .contentType("application/json")
                .content("{\"amountMinorUnits\":500,\"currency\":\"EUR\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void deposit_rejects_zero_amount() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", "k1")
                .contentType("application/json")
                .content("{\"amountMinorUnits\":0,\"currency\":\"GBP\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
