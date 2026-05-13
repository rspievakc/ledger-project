package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.account.AccountStatus;
import com.teya.ledger.domain.customer.CustomerId;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.AccountNotEmptyException;
import com.teya.ledger.domain.error.AccountNotFoundException;
import com.teya.ledger.infrastructure.port.EventStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
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
            return cache.load(accountId).orElseThrow();
        } finally {
            lock.unlock();
        }
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

    /**
     * Returns every account owned by {@code customerId}, including
     * closed ones — clients distinguish state via {@link Account#status}.
     *
     * <p>Enumerates the persistence layer's account streams (via
     * {@link EventStore#listStreams}) and folds each through the
     * {@link ProjectionCache}. This is O(total accounts) per call;
     * acceptable while account counts are modest. A future
     * {@code customer-accounts} index would let this scale linearly in
     * the result size instead.
     *
     * @param customerId the customer whose accounts to return.
     * @return all accounts for that customer, in unspecified order.
     * @throws com.teya.ledger.domain.error.CustomerNotFoundException
     *         if the customer does not exist — keeps the error envelope
     *         consistent with {@link #open}.
     */
    public List<Account> listByCustomer(CustomerId customerId) {
        // Validate up front so an unknown customer returns 404 rather
        // than a misleading empty list — same contract as `open()`.
        customers.find(customerId);
        List<Account> matches = new ArrayList<>();
        for (String streamId : eventStore.listStreams(ACCOUNT_STREAM_PREFIX)) {
            AccountId accountId = AccountId.of(
                streamId.substring(ACCOUNT_STREAM_PREFIX.length()));
            cache.load(accountId)
                .filter(a -> a.customerId().equals(customerId))
                .ifPresent(matches::add);
        }
        return List.copyOf(matches);
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
        if (account.status() != AccountStatus.OPEN) {
            throw new AccountClosedException(account.id());
        }
    }

    /**
     * Prefix shared by every per-account event stream id. Single
     * source of truth for {@link #streamId} and the
     * {@link #listByCustomer} enumeration — keep these in sync.
     */
    static final String ACCOUNT_STREAM_PREFIX = "account-";

    /**
     * Returns the canonical event-stream identifier for an account.
     * Package-private so sibling services (e.g. DepositService) and
     * {@link ProjectionCache#readAll} use the same naming convention.
     */
    static String streamId(AccountId accountId) {
        return ACCOUNT_STREAM_PREFIX + accountId;
    }
}
