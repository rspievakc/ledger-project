package com.teya.ledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.storage.type=in-memory")
class CustomerControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void create_returns_201_with_id_and_name() throws Exception {
        mvc.perform(post("/customer")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"name\":\"Alice\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId").exists())
            .andExpect(jsonPath("$.name").value("Alice"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void create_rejects_blank_name() throws Exception {
        mvc.perform(post("/customer")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void find_returns_404_for_unknown_customer() throws Exception {
        mvc.perform(get("/customer/" + java.util.UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void find_returns_the_customer_after_create() throws Exception {
        String body = mvc.perform(post("/customer")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"name\":\"Bob\"}"))
            .andReturn().getResponse().getContentAsString();
        // Extract customerId from JSON body via simple parse
        String customerId = body.replaceAll(".*\"customerId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mvc.perform(get("/customer/" + customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Bob"));
    }

    @Test
    void list_returns_the_customers_created_in_this_test() throws Exception {
        // Two uniquely-named customers — uses nanoTime so the assertion
        // stays specific even when prior tests in the @SpringBootTest
        // context have already created their own customers.
        String suffix = "-" + System.nanoTime();
        mvc.perform(post("/customer")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"name\":\"Listed-A" + suffix + "\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/customer")
                .header("Idempotency-Key", TestSetup.key())
                .contentType("application/json")
                .content("{\"name\":\"Listed-B" + suffix + "\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/customer"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name=='Listed-A" + suffix + "')]").exists())
            .andExpect(jsonPath("$[?(@.name=='Listed-B" + suffix + "')]").exists());
    }
}
