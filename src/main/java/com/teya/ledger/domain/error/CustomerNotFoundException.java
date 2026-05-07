package com.teya.ledger.domain.error;

import com.teya.ledger.domain.customer.CustomerId;

import java.util.Map;

/**
 * Lookup or operation referenced an unknown customer. Mapped to
 * {@code CUSTOMER_NOT_FOUND} (HTTP 404).
 */
public final class CustomerNotFoundException extends DomainException {

    public CustomerNotFoundException(CustomerId customerId) {
        super(
            "customer " + customerId + " not found",
            Map.of("customerId", customerId.toString())
        );
    }
}
