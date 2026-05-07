package com.teya.ledger.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * An immutable money value: a signed integer count of minor units in a
 * specific currency. {@code 5_00} GBP means £5.00; {@code -1} JPY means
 * ¥-1. Using integer minor units rather than {@code BigDecimal} avoids
 * any rounding ambiguity and matches the convention used by payment
 * processors.
 *
 * <p>Arithmetic methods refuse to mix currencies: {@code GBP.plus(EUR)}
 * throws an {@link IllegalArgumentException}. The same rule applies to
 * {@link #compareTo}.
 *
 * @param minorUnits signed integer count of the currency's minor unit
 *                   (e.g., pence for GBP, cents for USD).
 * @param currency   the ISO currency; must be non-null.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    /**
     * Compact constructor enforcing a non-null currency.
     */
    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
    }

    /**
     * Returns a zero-valued {@link Money} in the given currency.
     *
     * @param currency the ISO currency.
     * @return {@code Money(0, currency)}.
     */
    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    /**
     * Returns this plus {@code other}.
     *
     * @param other addend.
     * @return new {@link Money} with the summed minor units.
     * @throws IllegalArgumentException if currencies differ.
     * @throws ArithmeticException      on long overflow.
     */
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(this.minorUnits, other.minorUnits), currency);
    }

    /**
     * Returns this minus {@code other}.
     *
     * @param other subtrahend.
     * @return new {@link Money} with the difference of minor units.
     * @throws IllegalArgumentException if currencies differ.
     * @throws ArithmeticException      on long overflow.
     */
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(this.minorUnits, other.minorUnits), currency);
    }

    /**
     * Returns the additive inverse.
     *
     * @return new {@link Money} with negated minor units.
     */
    public Money negate() {
        return new Money(Math.negateExact(this.minorUnits), currency);
    }

    /**
     * Compares two {@link Money} values by minor units.
     *
     * @param other the other money value.
     * @return as per {@link Comparable#compareTo}.
     * @throws IllegalArgumentException if currencies differ.
     */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(this.minorUnits, other.minorUnits);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "currency mismatch: " + this.currency.getCurrencyCode()
                    + " vs " + other.currency.getCurrencyCode());
        }
    }
}
