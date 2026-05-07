package com.teya.ledger.api.dto;

import com.teya.ledger.domain.customer.Customer;

import java.time.Instant;

/**
 * Response body for customer endpoints.
 */
public record CustomerResponse(
    String customerId,
    String name,
    Instant createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.id().toString(), c.name(), c.createdAt());
    }
}
