package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.domain.account.AccountStatus;
import com.teya.ledger.domain.error.AccountClosedException;
import com.teya.ledger.domain.error.CurrencyMismatchException;
import com.teya.ledger.domain.error.InsufficientFundsException;
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
 * Application service for the withdrawal command. Validates against
 * the current projection (including the per-account overdraft limit),
 * appends a {@link AccountEvent.MoneyWithdrawn} event under the
 * per-account lock, and returns the post-event state.
 */
@Service
public class WithdrawalService {

    private final EventStore eventStore;
    private final EventEnvelopeMapper mapper;
    private final ProjectionCache cache;
    private final LockRegistry locks;
    private final AccountService accounts;
    private final Clock clock;

    public WithdrawalService(EventStore eventStore,
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
     * Withdraws {@code amountMinorUnits} from {@code accountId}.
     */
    public WithdrawalResult withdraw(AccountId accountId, long amountMinorUnits,
                                     Currency currency, String idempotencyKey) {
        return withdraw(accountId, amountMinorUnits, currency, idempotencyKey, clock.instant());
    }

    /**
     * Withdraws {@code amountMinorUnits} from {@code accountId}.
     */
    public WithdrawalResult withdraw(AccountId accountId, long amountMinorUnits,
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
            long projected = account.balanceMinorUnits() - amountMinorUnits;
            if (projected < -account.overdraftLimitMinorUnits()) {
                long available = account.balanceMinorUnits() + account.overdraftLimitMinorUnits();
                throw new InsufficientFundsException(accountId, amountMinorUnits, available);
            }
            AccountEvent.MoneyWithdrawn event = new AccountEvent.MoneyWithdrawn(
                accountId, amountMinorUnits, currency, when, idempotencyKey);
            EventRecord record = mapper.toRecord(event);
            AppendResult appended = eventStore.append(
                AccountService.streamId(accountId), List.of(record));
            cache.apply(accountId, event);
            return new WithdrawalResult(record.eventId(), appended.firstSeq(), projected);
        } finally {
            lock.unlock();
        }
    }
}
