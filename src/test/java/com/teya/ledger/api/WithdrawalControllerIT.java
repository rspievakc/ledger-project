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
class WithdrawalControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void withdrawal_within_balance_succeeds() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        TestSetup.deposit(mvc, accountId, 1_000L, TestSetup.key());
        mvc.perform(post("/account/" + accountId + "/withdrawal")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"amountMinorUnits\":300,\"currency\":\"GBP\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.balanceAfterMinorUnits").value(700));
    }

    @Test
    void withdrawal_breaching_balance_returns_insufficient_funds() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(post("/account/" + accountId + "/withdrawal")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"amountMinorUnits\":1,\"currency\":\"GBP\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void withdrawal_rejects_missing_idempotency_key() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(post("/account/" + accountId + "/withdrawal")
                .contentType("application/json")
                .content("{\"amountMinorUnits\":1,\"currency\":\"GBP\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }
}
