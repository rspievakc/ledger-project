package com.teya.ledger.application;

import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.CustomerEvent;
import com.teya.ledger.domain.customer.CustomerId;
import com.teya.ledger.infrastructure.port.EventRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges typed domain events ({@link AccountEvent}, {@link CustomerEvent})
 * to the opaque {@link EventRecord} format the {@code EventStore} port
 * persists, and back.
 *
 * <p>The {@code type} discriminator and {@code payload} keys are part
 * of the persisted contract: changing them would break replay against
 * existing event files. Add new event types and version fields if the
 * schema needs to evolve.
 */
@Component
public class EventEnvelopeMapper {

    /** Convert an {@link AccountEvent} into an unsequenced {@link EventRecord}. */
    public EventRecord toRecord(AccountEvent event) {
        return switch (event) {
            case AccountEvent.AccountOpened e -> EventRecord.unsequenced(
                UUID.randomUUID(), "AccountOpened", e.occurredAt(),
                ordered(
                    "accountId", e.accountId().toString(),
                    "customerId", e.customerId().toString(),
                    "currency", e.currency().getCurrencyCode(),
                    "initialOverdraftLimitMinorUnits", e.initialOverdraftLimitMinorUnits()
                ));
            case AccountEvent.MoneyDeposited e -> EventRecord.unsequenced(
                UUID.randomUUID(), "MoneyDeposited", e.occurredAt(),
                ordered(
                    "accountId", e.accountId().toString(),
                    "amountMinorUnits", e.amountMinorUnits(),
                    "currency", e.currency().getCurrencyCode(),
                    "idempotencyKey", e.idempotencyKey()
                ));
            case AccountEvent.MoneyWithdrawn e -> EventRecord.unsequenced(
                UUID.randomUUID(), "MoneyWithdrawn", e.occurredAt(),
                ordered(
                    "accountId", e.accountId().toString(),
                    "amountMinorUnits", e.amountMinorUnits(),
                    "currency", e.currency().getCurrencyCode(),
                    "idempotencyKey", e.idempotencyKey()
                ));
            case AccountEvent.OverdraftLimitChanged e -> EventRecord.unsequenced(
                UUID.randomUUID(), "OverdraftLimitChanged", e.occurredAt(),
                ordered(
                    "accountId", e.accountId().toString(),
                    "newLimitMinorUnits", e.newLimitMinorUnits()
                ));
            case AccountEvent.AccountClosed e -> EventRecord.unsequenced(
                UUID.randomUUID(), "AccountClosed", e.occurredAt(),
                ordered("accountId", e.accountId().toString()));
        };
    }

    /** Convert a {@link CustomerEvent} into an unsequenced {@link EventRecord}. */
    public EventRecord toRecord(CustomerEvent event) {
        return switch (event) {
            case CustomerEvent.CustomerCreated e -> EventRecord.unsequenced(
                UUID.randomUUID(), "CustomerCreated", e.occurredAt(),
                ordered(
                    "customerId", e.customerId().toString(),
                    "name", e.name()
                ));
        };
    }

    /** Decode an {@link EventRecord} back into an {@link AccountEvent}. */
    public AccountEvent toAccountEvent(EventRecord rec) {
        Map<String, Object> p = rec.payload();
        Instant ts = rec.occurredAt();
        AccountId accountId = AccountId.of(p.get("accountId").toString());
        return switch (rec.type()) {
            case "AccountOpened" -> new AccountEvent.AccountOpened(
                accountId,
                CustomerId.of(p.get("customerId").toString()),
                Currency.getInstance(p.get("currency").toString()),
                ((Number) p.get("initialOverdraftLimitMinorUnits")).longValue(),
                ts);
            case "MoneyDeposited" -> new AccountEvent.MoneyDeposited(
                accountId,
                ((Number) p.get("amountMinorUnits")).longValue(),
                Currency.getInstance(p.get("currency").toString()),
                ts,
                p.get("idempotencyKey").toString());
            case "MoneyWithdrawn" -> new AccountEvent.MoneyWithdrawn(
                accountId,
                ((Number) p.get("amountMinorUnits")).longValue(),
                Currency.getInstance(p.get("currency").toString()),
                ts,
                p.get("idempotencyKey").toString());
            case "OverdraftLimitChanged" -> new AccountEvent.OverdraftLimitChanged(
                accountId,
                ((Number) p.get("newLimitMinorUnits")).longValue(),
                ts);
            case "AccountClosed" -> new AccountEvent.AccountClosed(accountId, ts);
            default -> throw new IllegalStateException("unknown account event type: " + rec.type());
        };
    }

    /** Decode an {@link EventRecord} back into a {@link CustomerEvent}. */
    public CustomerEvent toCustomerEvent(EventRecord rec) {
        Map<String, Object> p = rec.payload();
        return switch (rec.type()) {
            case "CustomerCreated" -> new CustomerEvent.CustomerCreated(
                CustomerId.of(p.get("customerId").toString()),
                p.get("name").toString(),
                rec.occurredAt());
            default -> throw new IllegalStateException("unknown customer event type: " + rec.type());
        };
    }

    /**
     * Builds an insertion-ordered map from alternating key/value varargs.
     * Preserves field order in YAML serialization for readability.
     *
     * @param kvs alternating String keys and Object values; length must be even.
     * @return a new {@link LinkedHashMap} with the provided entries.
     */
    private static Map<String, Object> ordered(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put(kvs[i].toString(), kvs[i + 1]);
        }
        return m;
    }
}
