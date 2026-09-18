package ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(Currency currency, BigDecimal amount) {
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(currency.scale(), RoundingMode.UNNECESSARY);
    }

    public static Money of(Currency currency, String amount) {
        return new Money(currency, new BigDecimal(amount));
    }

    /** Explicit rounding boundary for calculated amounts, not input transfers. */
    public static Money rounded(Currency currency, BigDecimal amount) {
        return new Money(currency, amount.setScale(currency.scale(), ROUNDING));
    }

    public Money add(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(currency, amount.add(other.amount));
    }

    public Money negate() {
        return new Money(currency, amount.negate());
    }
}
