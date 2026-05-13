package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.account.AccountStatus;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.CurrencyMismatchException;
import com.teya.ledger.domain.error.InvalidAmountException;
import com.teya.ledger.infrastructure.port.AppendResult;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Application service for the deposit command. Validates against the
 * current projection, appends a {@link AccountEvent.MoneyDeposited}
 * event under the per-account lock, and returns the post-event state.
 */
@Service
public class DepositService {

    private final EventStore eventStore;
    private final EventEnvelopeMapper mapper;
    private final ProjectionCache cache;
    private final LockRegistry locks;
    private final AccountService accounts;
    private final Clock clock;

    public DepositService(EventStore eventStore,
                          EventEnvelopeMapper mapper,
                          ProjectionCache cache,
                          LockRegistry locks,
                          AccountService accounts,
                          Clock clock) {
        this.eventStore = eventStore;
        this.mapper = mapper;
        this.cache = cache;
        this.locks = locks;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Deposits {@code amountMinorUnits} into {@code accountId}.
     *
     * @throws InvalidAmountException        if amount {@code <= 0}.
     * @throws com.teya.ledger.domain.error.AccountNotFoundException if account does not exist.
     * @throws AccountClosedException        if the account is closed.
     * @throws CurrencyMismatchException     if the request currency differs from the account currency.
     */
    public DepositResult deposit(AccountId accountId, long amountMinorUnits,
                                 Currency currency, String idempotencyKey) {
        return deposit(accountId, amountMinorUnits, currency, idempotencyKey, clock.instant());
    }

    /**
     * Deposits {@code amountMinorUnits} into {@code accountId}.
     *
     * @throws InvalidAmountException        if amount {@code <= 0}.
     * @throws com.teya.ledger.domain.error.AccountNotFoundException if account does not exist.
     * @throws AccountClosedException        if the account is closed.
     * @throws CurrencyMismatchException     if the request currency differs from the account currency.
     */
    public DepositResult deposit(AccountId accountId, long amountMinorUnits,
                                  Currency currency, String idempotencyKey, Instant when) {
        if (amountMinorUnits <= 0L) {
            throw new InvalidAmountException(amountMinorUnits);
        }
        ReentrantLock lock = locks.lockFor(accountId);
        lock.lock();
        try {
            Account account = accounts.find(accountId);
            if (account.status() != AccountStatus.OPEN) {
                throw new AccountClosedException(accountId);
            }
            if (!account.currency().equals(currency)) {
                throw new CurrencyMismatchException(accountId, account.currency(), currency);
            }
            AccountEvent.MoneyDeposited event = new AccountEvent.MoneyDeposited(
                accountId, amountMinorUnits, currency, when, idempotencyKey);
            EventRecord record = mapper.toRecord(event);
            AppendResult appended = eventStore.append(
                AccountService.streamId(accountId), List.of(record));
            cache.apply(accountId, event);
            long newBalance = account.balanceMinorUnits() + amountMinorUnits;
            return new DepositResult(record.eventId(), appended.firstSeq(), newBalance);
        } finally {
            lock.unlock();
        }
    }
}
