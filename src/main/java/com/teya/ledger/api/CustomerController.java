package com.teya.ledger.api;

import com.teya.ledger.api.dto.CreateCustomerRequest;
import com.teya.ledger.api.dto.CustomerResponse;
import com.teya.ledger.api.idempotency.RequiresIdempotency;
import com.teya.ledger.application.CustomerService;
import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.domain.customer.CustomerId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for customer creation and lookup.
 *
 * <p>Resource segments are deliberately singular ({@code /customer},
 * not {@code /customers}); see {@code docs/architecture.md §7}.
 */
@RestController
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /** Create a customer. Body: {@code {"name": "Alice"}}. */
    @PostMapping
    @RequiresIdempotency
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest req) {
        Customer c = customers.create(req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(c));
    }

    /** Look up a customer by id. */
    @GetMapping("/{customerId}")
    public CustomerResponse find(@PathVariable("customerId") String customerId) {
        Customer c = customers.find(CustomerId.of(customerId));
        return CustomerResponse.from(c);
    }
}
