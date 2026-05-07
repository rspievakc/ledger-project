package com.teya.ledger.application;

import com.teya.ledger.domain.account.AccountId;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

class LockRegistryTest {

    @Test
    void same_account_returns_the_same_lock_instance() {
        LockRegistry reg = new LockRegistry();
        AccountId id = AccountId.random();
        ReentrantLock l1 = reg.lockFor(id);
        ReentrantLock l2 = reg.lockFor(id);
        assertThat(l1).isSameAs(l2);
    }

    @Test
    void different_accounts_get_different_locks() {
        LockRegistry reg = new LockRegistry();
        assertThat(reg.lockFor(AccountId.random()))
            .isNotSameAs(reg.lockFor(AccountId.random()));
    }

    @Test
    void same_account_serialises_concurrent_critical_sections()
        throws InterruptedException {
        LockRegistry reg = new LockRegistry();
        AccountId id = AccountId.random();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ReentrantLock lock = reg.lockFor(id);
                    lock.lock();
                    try {
                        int now = inside.incrementAndGet();
                        maxInside.accumulateAndGet(now, Math::max);
                        Thread.sleep(2);
                        inside.decrementAndGet();
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(maxInside.get())
            .as("at most one thread inside the critical section at a time")
            .isEqualTo(1);
    }

    @Test
    void distinct_accounts_run_in_parallel() throws InterruptedException {
        LockRegistry reg = new LockRegistry();
        int n = 8;
        AccountId[] ids = new AccountId[n];
        for (int i = 0; i < n; i++) ids[i] = AccountId.random();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch insideLatch = new CountDownLatch(n);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            AccountId id = ids[i];
            pool.submit(() -> {
                ReentrantLock lock = reg.lockFor(id);
                lock.lock();
                try {
                    insideLatch.countDown();
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                    done.countDown();
                }
            });
        }
        // All n threads acquire their distinct locks at once.
        assertThat(insideLatch.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
    }
}
