package com.teya.ledger.domain.account;

/**
 * Lifecycle state of an {@link Account}.
 *
 * <ul>
 *   <li>{@link #OPEN}: the account accepts deposits, withdrawals, and overdraft changes.</li>
 *   <li>{@link #CLOSED}: terminal state; no further mutations are accepted.</li>
 * </ul>
 */
public enum AccountStatus {
    OPEN,
    CLOSED
}
