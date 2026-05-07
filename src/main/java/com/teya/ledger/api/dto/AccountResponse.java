package com.teya.ledger.api.dto;

import com.teya.ledger.domain.account.Account;

/**
 * Response body for {@code GET /account/{id}} and the responses of
 * mutating account endpoints.
 */
public record AccountResponse(
    String accountId,
    String customerId,
    String currency,
    long overdraftLimitMinorUnits,
    long balanceMinorUnits,
    String status
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
            a.id().toString(),
            a.customerId().toString(),
            a.currency().getCurrencyCode(),
            a.overdraftLimitMinorUnits(),
            a.balanceMinorUnits(),
            a.status().name()
        );
    }
}
