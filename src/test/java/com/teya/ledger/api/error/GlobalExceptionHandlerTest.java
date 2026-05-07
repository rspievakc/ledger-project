package com.teya.ledger.api.error;

import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.error.AccountNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void account_not_found_yields_404_with_typed_code() {
        AccountId id = AccountId.random();
        ResponseEntity<ErrorResponse> response =
            handler.accountNotFound(new AccountNotFoundException(id));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(body.details()).containsEntry("accountId", id.toString());
    }
}
