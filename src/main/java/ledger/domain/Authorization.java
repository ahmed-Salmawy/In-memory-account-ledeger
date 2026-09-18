package ledger.domain;

import java.util.Objects;

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
            throw new IllegalArgumentException("Authorization and account IDs must not be blank");
        }
        if (createdDay < 1) {
            throw new IllegalArgumentException("Business day must be positive");
        }
        if (statusDay < createdDay) {
            throw new IllegalArgumentException("Status day cannot precede creation day");
        }
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Authorization amount must be positive");
        }
    }

    public AuthorizationStatus statusAt(int day) {
        if (day < 1) {
            throw new IllegalArgumentException("Business day must be positive");
        }
        return status == AuthorizationStatus.SETTLED && day < statusDay
                ? AuthorizationStatus.APPROVED : status;
    }
}
