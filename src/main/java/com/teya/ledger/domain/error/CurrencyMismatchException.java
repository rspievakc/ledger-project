package com.teya.ledger.domain.error;

import com.teya.ledger.domain.account.AccountId;

import java.util.Currency;
import java.util.Map;

/**
 * Request currency does not match the account's currency. Mapped to
 * {@code CURRENCY_MISMATCH} (HTTP 422).
 */
public final class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(AccountId accountId,
                                     Currency accountCurrency,
                                     Currency requestCurrency) {
        super(
            "request currency " + requestCurrency.getCurrencyCode()
                + " does not match account currency " + accountCurrency.getCurrencyCode(),
            Map.of(
                "accountId", accountId.toString(),
                "accountCurrency", accountCurrency.getCurrencyCode(),
                "requestCurrency", requestCurrency.getCurrencyCode()
            )
        );
    }
}
