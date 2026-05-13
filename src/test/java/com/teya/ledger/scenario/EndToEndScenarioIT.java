package com.teya.ledger.scenario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.storage.type=in-memory")
class EndToEndScenarioIT {

    @Autowired
    MockMvc mvc;

    @Test
    void full_lifecycle_open_deposit_history_close() throws Exception {
        // 1. Create customer
        String customerBody = mvc.perform(post("/customer")
                .header("Idempotency-Key", key())
                .contentType("application/json")
                .content("{\"name\":\"Alice\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String customerId = extract(customerBody, "customerId");

        // 2. Open two GBP accounts
        String accountA = openAccount(customerId, "GBP", 0L);
        String accountB = openAccount(customerId, "GBP", 0L);

        // 2a. Both accounts surface in the per-customer listing, matching
        // the README walk-through's "list accounts for a customer" step.
        mvc.perform(get("/customer/" + customerId + "/account"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.accountId=='" + accountA + "')]").exists())
            .andExpect(jsonPath("$[?(@.accountId=='" + accountB + "')]").exists());

        // 2b. The customer surfaces in the global listing, matching
        // the README walk-through's "list all customers" step.
        mvc.perform(get("/customer"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.customerId=='" + customerId + "')]").exists());

        // 3. Deposit on each
        deposit(accountA, 1_000L);
        deposit(accountB, 500L);

        // 4. Cross-currency withdrawal must be rejected
        mvc.perform(post("/account/" + accountA + "/withdrawal")
                .header("Idempotency-Key", key())
                .contentType("application/json")
                .content("{\"amountMinorUnits\":100,\"currency\":\"EUR\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        // 5. Paginated history returns events in order
        mvc.perform(get("/account/" + accountA + "/transaction"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].type").value("MoneyDeposited"))
            .andExpect(jsonPath("$.items[0].amountMinorUnits").value(1000));

        // 6. Closing with non-zero balance must be rejected
        mvc.perform(delete("/account/" + accountA)
                .header("Idempotency-Key", key()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_EMPTY"));

        // 7. Withdraw to zero
        mvc.perform(post("/account/" + accountA + "/withdrawal")
                .header("Idempotency-Key", key())
                .contentType("application/json")
                .content("{\"amountMinorUnits\":1000,\"currency\":\"GBP\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.balanceAfterMinorUnits").value(0));

        // 8. Close succeeds
        mvc.perform(delete("/account/" + accountA)
                .header("Idempotency-Key", key()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    private String openAccount(String customerId, String currency, long overdraft) throws Exception {
        String body = mvc.perform(post("/customer/" + customerId + "/account")
                .header("Idempotency-Key", key())
                .contentType("application/json")
                .content("{\"currency\":\"" + currency + "\",\"overdraftLimitMinorUnits\":" + overdraft + "}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return extract(body, "accountId");
    }

    private void deposit(String accountId, long amount) throws Exception {
        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", key())
                .contentType("application/json")
                .content("{\"amountMinorUnits\":" + amount + ",\"currency\":\"GBP\"}"))
            .andExpect(status().isCreated());
    }

    private static String key() {
        return "k-" + UUID.randomUUID();
    }

    private static String extract(String json, String key) {
        return json.replaceAll(".*\"" + key + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
