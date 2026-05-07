package com.teya.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void constructs_with_minor_units_and_currency() {
        Money m = new Money(5_00L, GBP);
        assertThat(m.minorUnits()).isEqualTo(500L);
        assertThat(m.currency()).isEqualTo(GBP);
    }

    @Test
    void zero_constructs_for_any_currency() {
        Money m = Money.zero(GBP);
        assertThat(m.minorUnits()).isZero();
        assertThat(m.currency()).isEqualTo(GBP);
    }

    @Test
    void plus_adds_minor_units_when_currencies_match() {
        Money a = new Money(100L, GBP);
        Money b = new Money(250L, GBP);
        assertThat(a.plus(b)).isEqualTo(new Money(350L, GBP));
    }

    @Test
    void minus_subtracts_minor_units_when_currencies_match() {
        Money a = new Money(500L, GBP);
        Money b = new Money(150L, GBP);
        assertThat(a.minus(b)).isEqualTo(new Money(350L, GBP));
    }

    @Test
    void negate_flips_sign_and_keeps_currency() {
        Money m = new Money(123L, GBP);
        assertThat(m.negate()).isEqualTo(new Money(-123L, GBP));
    }

    @Test
    void plus_rejects_currency_mismatch() {
        Money gbp = new Money(100L, GBP);
        Money eur = new Money(100L, EUR);
        assertThatThrownBy(() -> gbp.plus(eur))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GBP")
            .hasMessageContaining("EUR");
    }

    @Test
    void minus_rejects_currency_mismatch() {
        Money gbp = new Money(100L, GBP);
        Money eur = new Money(100L, EUR);
        assertThatThrownBy(() -> gbp.minus(eur))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GBP")
            .hasMessageContaining("EUR");
    }

    @Test
    void compare_to_orders_by_minor_units_when_currencies_match() {
        Money a = new Money(100L, GBP);
        Money b = new Money(200L, GBP);
        assertThat(a.compareTo(b)).isNegative();
        assertThat(b.compareTo(a)).isPositive();
        assertThat(a.compareTo(new Money(100L, GBP))).isZero();
    }

    @Test
    void compare_to_rejects_currency_mismatch() {
        Money gbp = new Money(100L, GBP);
        Money eur = new Money(100L, EUR);
        assertThatThrownBy(() -> gbp.compareTo(eur))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GBP")
            .hasMessageContaining("EUR");
    }

    @Test
    void plus_rejects_overflow() {
        Money max = new Money(Long.MAX_VALUE, GBP);
        Money one = new Money(1L, GBP);
        assertThatThrownBy(() -> max.plus(one))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void requires_non_null_currency() {
        assertThatThrownBy(() -> new Money(100L, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equals_includes_currency() {
        Money gbp100 = new Money(100L, GBP);
        Money eur100 = new Money(100L, EUR);
        assertThat(gbp100).isNotEqualTo(eur100);
    }

    @Test
    void negate_rejects_min_value_overflow() {
        Money min = new Money(Long.MIN_VALUE, GBP);
        assertThatThrownBy(min::negate)
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void plus_rejects_null() {
        Money m = new Money(100L, GBP);
        assertThatThrownBy(() -> m.plus(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("other");
    }

    @Test
    void minus_rejects_null() {
        Money m = new Money(100L, GBP);
        assertThatThrownBy(() -> m.minus(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("other");
    }

    @Test
    void compare_to_rejects_null() {
        Money m = new Money(100L, GBP);
        assertThatThrownBy(() -> m.compareTo(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("other");
    }
}
