package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.customer.CustomerId;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.AccountNotEmptyException;
import com.teya.ledger.domain.error.AccountNotFoundException;
import com.teya.ledger.infrastructure.port.EventStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Application service for the lifecycle of accounts: open, find,
 * change overdraft, close.
 */
@Service
public class AccountService {

    private final EventStore eventStore;
    private final EventEnvelopeMapper mapper;
    private final ProjectionCache cache;
    private final LockRegistry locks;
    private final CustomerService customers;
    private final Clock clock;

    public AccountService(EventStore eventStore,
                          EventEnvelopeMapper mapper,
                          ProjectionCache cache,
                          LockRegistry locks,
                          CustomerService customers,
                          Clock clock) {
        this.eventStore = eventStore;
        this.mapper = mapper;
        this.cache = cache;
        this.locks = locks;
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Opens a new account for {@code customerId}. The account starts
     * with a zero balance.
     *
     * @throws com.teya.ledger.domain.error.CustomerNotFoundException if the customer does not exist.
     */
    public Account open(CustomerId customerId, Currency currency, long overdraftLimitMinorUnits) {
        customers.find(customerId);
        AccountId accountId = AccountId.random();
        AccountEvent.AccountOpened opened = new AccountEvent.AccountOpened(
            accountId, customerId, currency, overdraftLimitMinorUnits, clock.instant());
        ReentrantLock lock = locks.lockFor(accountId);
        lock.lock();
        try {
            eventStore.append(streamId(accountId), List.of(mapper.toRecord(opened)));
            cache.apply(accountId, opened);
        } finally {
            lock.unlock();
        }
        return cache.load(accountId).orElseThrow();
    }

    /**
     * Looks up the current state of {@code accountId}.
     *
     * @throws AccountNotFoundException if the account does not exist.
     */
    public Account find(AccountId accountId) {
        return cache.load(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    /** Changes the overdraft limit on an existing open account. */
    public Account changeOverdraft(AccountId accountId, long newLimitMinorUnits) {
        ReentrantLock lock = locks.lockFor(accountId);
        lock.lock();
        try {
            Account current = find(accountId);
            requireOpen(current);
            AccountEvent.OverdraftLimitChanged event =
                new AccountEvent.OverdraftLimitChanged(
                    accountId, newLimitMinorUnits, clock.instant());
            eventStore.append(streamId(accountId), List.of(mapper.toRecord(event)));
            cache.apply(accountId, event);
            return cache.load(accountId).orElseThrow();
        } finally {
            lock.unlock();
        }
    }

    /** Closes an account; refuses unless the balance is exactly zero. */
    public Account close(AccountId accountId) {
        ReentrantLock lock = locks.lockFor(accountId);
        lock.lock();
        try {
            Account current = find(accountId);
            requireOpen(current);
            if (current.balanceMinorUnits() != 0L) {
                throw new AccountNotEmptyException(accountId, current.balanceMinorUnits());
            }
            AccountEvent.AccountClosed event =
                new AccountEvent.AccountClosed(accountId, clock.instant());
            eventStore.append(streamId(accountId), List.of(mapper.toRecord(event)));
            cache.apply(accountId, event);
            return cache.load(accountId).orElseThrow();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Throws {@link AccountClosedException} if {@code account} is not in the OPEN status.
     * Called by {@link #changeOverdraft} and {@link #close} before any mutation.
     */
    private static void requireOpen(Account account) {
        if (account.status() != com.teya.ledger.domain.account.AccountStatus.OPEN) {
            throw new AccountClosedException(account.id());
        }
    }

    /**
     * Returns the canonical event-stream identifier for an account.
     * Package-private so sibling services (e.g. DepositService) and
     * {@link ProjectionCache#readAll} use the same naming convention.
     */
    static String streamId(AccountId accountId) {
        return "account-" + accountId;
    }
}
