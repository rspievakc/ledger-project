package com.teya.ledger.api;

import com.teya.ledger.api.dto.CreateCustomerRequest;
import com.teya.ledger.api.dto.CustomerResponse;
import com.teya.ledger.api.error.ErrorResponse;
import com.teya.ledger.api.idempotency.RequiresIdempotency;
import com.teya.ledger.application.CustomerService;
import com.teya.ledger.domain.customer.Customer;
import com.teya.ledger.domain.customer.CustomerId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP endpoints for customer creation and lookup.
 *
 * <p>Resource segments are deliberately singular ({@code /customer},
 * not {@code /customers}); see {@code docs/architecture.md §7}.
 */
@RestController
@RequestMapping("/customer")
@Tag(name = "Customer", description = "Customer creation and lookup")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /** Create a customer. Body: {@code {"name": "Alice"}}. */
    @PostMapping
    @RequiresIdempotency
    @Operation(summary = "Create a customer", description = "Requires Idempotency-Key header.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Customer created"),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key or invalid body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Idempotency-Key reused with a different body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest req) {
        Customer c = customers.create(req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(c));
    }

    /** List every customer, oldest first. */
    @GetMapping
    @Operation(summary = "List all customers",
        description = "Returns every customer that has been created, in creation order. "
            + "Unpaginated; intended for the small-scale walk-through and admin use.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer list")
    })
    public List<CustomerResponse> list() {
        return customers.listAll().stream()
            .map(CustomerResponse::from)
            .toList();
    }

    /** Look up a customer by id. */
    @GetMapping("/{customerId}")
    @Operation(summary = "Look up a customer by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer"),
        @ApiResponse(responseCode = "404", description = "Unknown customerId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CustomerResponse find(@PathVariable("customerId") String customerId) {
        Customer c = customers.find(CustomerId.of(customerId));
        return CustomerResponse.from(c);
    }
}
