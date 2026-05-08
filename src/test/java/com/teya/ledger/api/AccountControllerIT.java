package com.teya.ledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.storage.type=in-memory")
class AccountControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void open_then_get_returns_account_with_zero_balance() throws Exception {
        String customerId = createCustomer("Alice");
        MvcResult opened = mvc.perform(post("/customer/" + customerId + "/account")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"currency\":\"GBP\",\"overdraftLimitMinorUnits\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.balanceMinorUnits").value(0))
            .andExpect(jsonPath("$.currency").value("GBP"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn();
        String accountId = extract(opened.getResponse().getContentAsString(), "accountId");
        mvc.perform(get("/account/" + accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanceMinorUnits").value(0));
    }

    @Test
    void get_returns_404_for_unknown_account() throws Exception {
        mvc.perform(get("/account/" + java.util.UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void open_for_unknown_customer_returns_404() throws Exception {
        mvc.perform(post("/customer/" + java.util.UUID.randomUUID() + "/account")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"currency\":\"GBP\",\"overdraftLimitMinorUnits\":0}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void patch_overdraft_updates_limit() throws Exception {
        String accountId = newAccount("GBP", 0);
        mvc.perform(patch("/account/" + accountId + "/overdraft-limit")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"newLimitMinorUnits\":5000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overdraftLimitMinorUnits").value(5000));
    }

    @Test
    void delete_succeeds_when_balance_is_zero() throws Exception {
        String accountId = newAccount("GBP", 0);
        mvc.perform(delete("/account/" + accountId)
                .header("Idempotency-Key", TestSetup.key()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void open_rejects_invalid_currency_code() throws Exception {
        String customerId = createCustomer("X");
        mvc.perform(post("/customer/" + customerId + "/account")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"currency\":\"\",\"overdraftLimitMinorUnits\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String createCustomer(String name) throws Exception {
        return extract(
            mvc.perform(post("/customer")
                    .header("Idempotency-Key", TestSetup.key())
                    .contentType("application/json")
                    .content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString(),
            "customerId");
    }

    private String newAccount(String currency, long overdraft) throws Exception {
        String customerId = createCustomer("U" + System.nanoTime());
        return extract(
            mvc.perform(post("/customer/" + customerId + "/account")
                    .header("Idempotency-Key", TestSetup.key())
                    .contentType("application/json")
                    .content("{\"currency\":\"" + currency + "\",\"overdraftLimitMinorUnits\":" + overdraft + "}"))
                .andReturn().getResponse().getContentAsString(),
            "accountId");
    }

    static String extract(String json, String key) {
        return json.replaceAll(".*\"" + key + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
