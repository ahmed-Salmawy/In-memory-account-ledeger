package ledger.domain;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @ParameterizedTest
    @CsvSource({"AED, 1200, 1200.00", "AED, 1.2300, 1.23",
            "BHD, 10, 10.000", "BHD, 3.3340, 3.334",
            "AED, -950, -950.00", "BHD, 0, 0.000"})
    void normalizesExactInputToCurrencyScale(Currency currency, String input, String expected) {
        Money money = Money.of(currency, input);
        assertEquals(new BigDecimal(expected), money.amount());
        assertEquals(currency, money.currency());
    }

    @ParameterizedTest
    @CsvSource({"AED, 1.001", "AED, -1.001", "BHD, 1.0001"})
    void rejectsInputThatWouldLoseMoney(Currency currency, String input) {
        assertThrows(ArithmeticException.class, () -> Money.of(currency, input));
    }

    @ParameterizedTest
    @CsvSource({"AED, 1.225, 1.22", "AED, 1.235, 1.24",
            "AED, -1.225, -1.22", "AED, -1.235, -1.24",
            "BHD, 1.2345, 1.234", "BHD, 1.2355, 1.236"})
    void explicitlyRoundsCalculatedValuesHalfEven(Currency currency, String input, String expected) {
        assertEquals(Money.of(currency, expected), Money.rounded(currency, new BigDecimal(input)));
    }

    @Test
    void addsAndNegatesExactlyWithoutChangingOperands() {
        Money first = Money.of(AED, "0.10");
        Money second = Money.of(AED, "0.20");
        assertEquals(Money.of(AED, "0.30"), first.add(second));
        assertEquals(Money.of(AED, "-0.10"), first.negate());
        assertEquals(Money.of(AED, "0.10"), first);
        assertEquals(Money.of(AED, "0.20"), second);
    }

    @Test
    void rejectsCrossCurrencyArithmetic() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(AED, "1").add(Money.of(BHD, "1")));
    }

    @Test
    void rejectsMissingCurrencyOrAmount() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Money(null, BigDecimal.ONE)),
                () -> assertThrows(NullPointerException.class, () -> new Money(AED, null)));
    }
}
