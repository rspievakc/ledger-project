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

    // 200 is the maximum page size the EventStore contract permits and
    // the same upper bound used by the API's GET /account/{id}/transaction
    // endpoint. Reading at the maximum minimises round-trips during a
    // full stream fold.
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
     * if the account stream contains no events — callers should map this
     * to {@code ACCOUNT_NOT_FOUND}.
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
        // Concurrent get-then-put race here is benign: two threads on a
        // cold key will both fold the same prefix of the (immutable,
        // append-only) event stream and produce byte-identical Accounts.
        // Last-writer-wins keeps the cache correct without serialising
        // reads through a lock.
        snapshots.put(accountId, folded);
        return Optional.of(folded);
    }

    /**
     * Applies a freshly-appended event to the cached projection so the
     * next {@link #load} returns the post-event state without going to
     * the store.
     *
     * <p>Must be called <em>after</em> the event has been persisted via
     * {@link EventStore#append}. The command-handler order is always
     * {@code load → validate → append → apply}; the fallback fold
     * branch below depends on this ordering.
     */
    public void apply(AccountId accountId, AccountEvent event) {
        // ConcurrentHashMap.compute holds a bin-level lock for the
        // mapping function. The fallback fold path below performs
        // EventStore I/O while holding that lock; under InMemoryEventStore
        // this is fast, under YamlEventStore it touches disk. The path is
        // a recovery fallback (it only runs if invalidate() raced an
        // in-flight write), so contention is rare in normal traffic. If
        // the YamlEventStore becomes the primary production adapter,
        // restructure this to fold outside compute and use replace().
        snapshots.compute(accountId, (k, prior) -> {
            if (prior == null) {
                // Cache was invalidated between this caller's load() and
                // now. Because append() always precedes apply() (the
                // command handler order), the just-written event is
                // already in the stream — readAll() will include it and
                // foldFrom() yields the post-event state, identical to
                // what prior.apply(event) would have returned.
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

    /**
     * Reads every event for {@code accountId}, decoded into the typed
     * {@link AccountEvent} hierarchy. Pages internally via
     * {@link #PAGE_SIZE}, advancing the cursor by each record's
     * {@code seq}; an empty or short page ends the read.
     */
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
