package com.teya.ledger.application;

import com.teya.ledger.domain.account.AccountId;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-account {@link ReentrantLock} registry. Command handlers
 * acquire {@code lockFor(accountId)} for the duration of
 * {@code load → validate → append → cache update}, ensuring writes
 * to the same account are linearisable while writes to distinct
 * accounts run in parallel.
 *
 * <p>Locks are kept for the lifetime of the process; the number of
 * accounts ever touched is bounded by the small problem size.
 */
@Component
public class LockRegistry {

    private final ConcurrentMap<AccountId, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * @param accountId account whose write critical section is being entered.
     * @return the singleton lock for that account.
     */
    public ReentrantLock lockFor(AccountId accountId) {
        return locks.computeIfAbsent(accountId, k -> new ReentrantLock());
    }
}
