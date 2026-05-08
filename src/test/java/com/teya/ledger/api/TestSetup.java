package com.teya.ledger.api;

import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Shared helpers for HTTP integration tests. */
final class TestSetup {

    private TestSetup() {
    }

    /** A fresh idempotency key for one test-internal write call. */
    static String key() {
        return "k-" + UUID.randomUUID();
    }

    static String openGbpAccount(MockMvc mvc) throws Exception {
        String customerBody = mvc.perform(post("/customer")
                .header("Idempotency-Key", key())
                .contentType("application/json")
                .content("{\"name\":\"U" + System.nanoTime() + "\"}"))
            .andReturn().getResponse().getContentAsString();
        String customerId = AccountControllerIT.extract(customerBody, "customerId");
        String accountBody = mvc.perform(post("/customer/" + customerId + "/account")
                .header("Idempotency-Key", key())
                .contentType("application/json")
                .content("{\"currency\":\"GBP\",\"overdraftLimitMinorUnits\":0}"))
            .andReturn().getResponse().getContentAsString();
        return AccountControllerIT.extract(accountBody, "accountId");
    }

    static void deposit(MockMvc mvc, String accountId, long amount, String key) throws Exception {
        mvc.perform(post("/account/" + accountId + "/deposit")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"amountMinorUnits\":" + amount + ",\"currency\":\"GBP\"}"))
            .andReturn();
    }
}
