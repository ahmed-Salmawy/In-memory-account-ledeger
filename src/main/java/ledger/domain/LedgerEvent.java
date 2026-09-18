package ledger.domain;

import java.util.Objects;

public record LedgerEvent(String eventId, int postedDay, int valueDay,
                          EventType type, String accountId, Money amount) {
    public LedgerEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        if (eventId.isBlank() || accountId.isBlank()) {
            throw new IllegalArgumentException("Event and account IDs must not be blank");
        }
        if (postedDay < 1 || valueDay < 1) {
            throw new IllegalArgumentException("Business days must be positive");
        }
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Input transfer amount must be positive");
        }
    }
}
