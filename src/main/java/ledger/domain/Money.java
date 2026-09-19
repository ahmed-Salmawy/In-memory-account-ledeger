package ledger.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ledger.domain.enums.Currency;
import ledger.domain.exception.LedgerArgumentException;

/**
 * Exact monetary amount: always at the currency's scale, never floating point.
 * Input precision loss is rejected; only calculated amounts round, HALF_EVEN.
 */
public record Money(Currency currency, BigDecimal amount) {
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    /**
     * Rejects amounts that cannot be represented at the currency's scale;
     * extra trailing zeros are exact and accepted.
     */
    public Money {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(currency.scale(), RoundingMode.UNNECESSARY);
    }

    /** Parses a decimal literal at the currency's exact scale. */
    public static Money of(Currency currency, String amount) {
        return new Money(currency, new BigDecimal(amount));
    }

    /** Explicit rounding boundary for calculated amounts, not input transfers. */
    public static Money rounded(Currency currency, BigDecimal amount) {
        return new Money(currency, amount.setScale(currency.scale(), ROUNDING));
    }

    /** Exact addition; mixed currencies are a programming error, not a business rejection. */
    public Money add(Money other) {
        if (currency != other.currency) {
            throw new LedgerArgumentException("Cannot add different currencies");
        }
        return new Money(currency, amount.add(other.amount));
    }

    /** Sign flip at the same scale — the basis of debit booking and reversals. */
    public Money negate() {
        return new Money(currency, amount.negate());
    }

    /** Splits the exact minor units, assigning any residual to the earliest parts. */
    public List<Money> allocate(int parts) {
        if (parts < 1) {
            throw new LedgerArgumentException("Parts must be positive");
        }
        BigInteger units = amount.movePointRight(currency.scale()).toBigIntegerExact();
        BigInteger[] division = units.divideAndRemainder(BigInteger.valueOf(parts));
        List<Money> result = new ArrayList<>(parts);
        int residual = division[1].abs().intValueExact();
        BigInteger step = BigInteger.valueOf(division[1].signum());
        for (int index = 0; index < parts; index++) {
            BigInteger part = division[0].add(index < residual ? step : BigInteger.ZERO);
            result.add(new Money(currency, new BigDecimal(part, currency.scale())));
        }
        return List.copyOf(result);
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
