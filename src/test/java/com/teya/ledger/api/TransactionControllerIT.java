package com.teya.ledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.storage.type=in-memory")
class TransactionControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void empty_history_returns_empty_items_and_null_cursor() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(get("/account/" + accountId + "/transaction"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void cursor_pagination_walks_through_all_events() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        for (int i = 0; i < 5; i++) {
            TestSetup.deposit(mvc, accountId, 100L, "k" + i);
        }
        MvcResult page1 = mvc.perform(get("/account/" + accountId + "/transaction")
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.nextCursor").exists())
            .andReturn();
        long cursor1 = extractCursor(page1.getResponse().getContentAsString());

        MvcResult page2 = mvc.perform(get("/account/" + accountId + "/transaction")
                .param("after", String.valueOf(cursor1))
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn();
        long cursor2 = extractCursor(page2.getResponse().getContentAsString());

        mvc.perform(get("/account/" + accountId + "/transaction")
                .param("after", String.valueOf(cursor2))
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void rejects_oversized_limit() throws Exception {
        String accountId = TestSetup.openGbpAccount(mvc);
        mvc.perform(get("/account/" + accountId + "/transaction")
                .param("limit", "201"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private static long extractCursor(String body) {
        return Long.parseLong(body.replaceAll(".*\"nextCursor\"\\s*:\\s*(\\d+).*", "$1"));
    }
}
