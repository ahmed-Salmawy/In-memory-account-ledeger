package ledger.domain;

import java.util.Objects;

public record Account(String accountId, Money openingBalance) {
    public Account {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(openingBalance, "openingBalance");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
    }

    public Currency currency() {
        return openingBalance.currency();
    }
}
