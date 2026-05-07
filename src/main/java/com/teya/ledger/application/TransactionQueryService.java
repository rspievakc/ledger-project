package com.teya.ledger.application;

import com.teya.ledger.domain.account.AccountEvent;
import com.teya.ledger.domain.account.AccountId;
import com.teya.ledger.infrastructure.port.EventRecord;
import com.teya.ledger.infrastructure.port.EventStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only application service for paginated account transaction
 * history. Surfaces only money-movement events
 * ({@link AccountEvent.MoneyDeposited}, {@link AccountEvent.MoneyWithdrawn});
 * lifecycle events such as overdraft changes and account closure are
 * intentionally hidden from this view.
 */
@Service
public class TransactionQueryService {

    static final int MAX_LIMIT = 200;

    private final EventStore eventStore;
    private final EventEnvelopeMapper mapper;

    public TransactionQueryService(EventStore eventStore, EventEnvelopeMapper mapper) {
        this.eventStore = eventStore;
        this.mapper = mapper;
    }

    /**
     * Returns one page of money events for {@code accountId}.
     *
     * @param accountId account whose history to read.
     * @param afterSeq  exclusive lower bound on returned seq.
     * @param limit     page size, in {@code [1, 200]}.
     * @return the page; {@code nextCursor} non-null if more events exist.
     */
    public TransactionPage history(AccountId accountId, long afterSeq, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be in [1," + MAX_LIMIT + "]");
        }
        String streamId = AccountService.streamId(accountId);
        // Collect up to limit + 1 money items: the extra one (if found) tells
        // us "there's another money event after this page", which becomes
        // nextCursor; we drop it from the returned items. Lifecycle events
        // (AccountOpened, OverdraftLimitChanged, AccountClosed) are skipped
        // silently and don't count toward limit. Read in chunks of limit+1
        // to keep round-trip count bounded even when many lifecycle events
        // are interleaved.
        List<TransactionPage.Item> items = new ArrayList<>(limit + 1);
        long cursor = afterSeq;
        int chunkSize = limit + 1;
        while (items.size() < limit + 1) {
            List<EventRecord> page = eventStore.readFrom(streamId, cursor, chunkSize);
            if (page.isEmpty()) break;
            for (EventRecord rec : page) {
                cursor = rec.seq();
                TransactionPage.Item item = toItem(mapper.toAccountEvent(rec), rec.seq());
                if (item == null) continue;
                items.add(item);
                if (items.size() == limit + 1) break;
            }
            if (items.size() == limit + 1) break;
            if (page.size() < chunkSize) break;
        }
        boolean more = items.size() > limit;
        if (more) {
            items.remove(items.size() - 1);
        }
        Long nextCursor = more ? items.get(items.size() - 1).seq() : null;
        return new TransactionPage(List.copyOf(items), nextCursor);
    }

    /** Maps an AccountEvent to a public Item with its persisted seq, or null for non-money events. */
    private static TransactionPage.Item toItem(AccountEvent event, long seq) {
        return switch (event) {
            case AccountEvent.MoneyDeposited e -> new TransactionPage.Item(
                seq, "MoneyDeposited", e.amountMinorUnits(), e.currency(), e.occurredAt());
            case AccountEvent.MoneyWithdrawn e -> new TransactionPage.Item(
                seq, "MoneyWithdrawn", e.amountMinorUnits(), e.currency(), e.occurredAt());
            default -> null;
        };
    }
}
