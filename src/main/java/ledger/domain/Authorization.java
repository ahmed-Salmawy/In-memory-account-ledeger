package ledger.domain;

import java.util.Objects;
import ledger.domain.enums.AuthorizationStatus;
import ledger.domain.exception.LedgerArgumentException;

/** An approved authorization is an active hold, not a booked debit. */
public record Authorization(String authorizationId, String accountId, Money amount,
                            int createdDay, AuthorizationStatus status, int statusDay) {
    public Authorization(String authorizationId, String accountId, Money amount,
                         int createdDay, AuthorizationStatus status) {
        this(authorizationId, accountId, amount, createdDay, status, createdDay);
    }

    public Authorization {
        Objects.requireNonNull(authorizationId, "authorizationId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        if (authorizationId.isBlank() || accountId.isBlank()) {
            throw new LedgerArgumentException("Authorization and account IDs must not be blank");
        }
        if (createdDay < 1) {
            throw new LedgerArgumentException("Business day must be positive");
        }
        if (statusDay < createdDay) {
            throw new LedgerArgumentException("Status day cannot precede creation day");
        }
        if (amount.amount().signum() <= 0) {
            throw new LedgerArgumentException("Authorization amount must be positive");
        }
    }

    /**
     * Historical view: a hold settled on a later day still counts as APPROVED
     * for earlier days, so past available balances reconstruct correctly.
     */
    public AuthorizationStatus statusAt(int day) {
        if (day < 1) {
            throw new LedgerArgumentException("Business day must be positive");
        }
        return status == AuthorizationStatus.SETTLED && day < statusDay
                ? AuthorizationStatus.APPROVED : status;
    }
}
