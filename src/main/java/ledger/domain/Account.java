package ledger.domain;

import java.util.Objects;
import ledger.domain.enums.Currency;
import ledger.domain.exception.LedgerArgumentException;

/** Immutable account configuration: identity, explicit currency, opening balance. */
public record Account(String accountId, Currency currency, Money openingBalance) {
    public Account {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(openingBalance, "openingBalance");
        if (accountId.isBlank()) {
            throw new LedgerArgumentException("accountId must not be blank");
        }
        if (currency != openingBalance.currency()) {
            throw new LedgerArgumentException("currency must match the opening balance currency");
        }
    }
}
