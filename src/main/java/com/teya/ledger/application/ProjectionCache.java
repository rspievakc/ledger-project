package com.teya.ledger.application;

import com.teya.ledger.domain.account.Account;
import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory cache of {@link Account} projections, keyed by account id.
 *
 * <p>Cache entries are only mutated by callers holding the per-account
 * lock from {@link LockRegistry}, so all reads/writes within that
 * critical section see a consistent view. Callers that bypass the
 * lock (e.g., a read-only query) must call {@link #invalidate} to
 * force a reload after any external change.
 */
@Component
public class ProjectionCache {

    private static final int PAGE_SIZE = 200;

    private final EventStore eventStore;
    private final EventEnvelopeMapper mapper;
    private final ConcurrentMap<AccountId, Account> snapshots = new ConcurrentHashMap<>();

    public ProjectionCache(EventStore eventStore, EventEnvelopeMapper mapper) {
        this.eventStore = eventStore;
        this.mapper = mapper;
    }

    /**
     * Returns the current projection for {@code accountId}, folding from
     * the store if the cache is cold. Returns {@code Optional.empty()}
     * if the account stream contains no events.
     */
    public Optional<Account> load(AccountId accountId) {
        Account cached = snapshots.get(accountId);
        if (cached != null) {
            return Optional.of(cached);
        }
        List<AccountEvent> events = readAll(accountId);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        Account folded = Account.foldFrom(events);
        snapshots.put(accountId, folded);
        return Optional.of(folded);
    }

    /**
     * Applies a freshly-appended event to the cached projection so the
     * next {@link #load} returns the post-event state without going to
     * the store.
     */
    public void apply(AccountId accountId, AccountEvent event) {
        snapshots.compute(accountId, (k, prior) -> {
            if (prior == null) {
                return Account.foldFrom(readAll(accountId));
            }
            return prior.apply(event);
        });
    }

    /**
     * Drops the cached projection for {@code accountId}; the next
     * {@link #load} will re-read events from the store.
     */
    public void invalidate(AccountId accountId) {
        snapshots.remove(accountId);
    }

    private List<AccountEvent> readAll(AccountId accountId) {
        String streamId = "account-" + accountId;
        List<AccountEvent> events = new ArrayList<>();
        long after = 0L;
        while (true) {
            List<EventRecord> page = eventStore.readFrom(streamId, after, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (EventRecord rec : page) {
                events.add(mapper.toAccountEvent(rec));
                after = rec.seq();
            }
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return events;
    }
}
