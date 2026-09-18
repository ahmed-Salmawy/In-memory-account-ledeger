package ledger.domain;

import java.util.Objects;

public record LedgerEntry(String entryId, String sourceEventId, String accountId,
                          Money amount, int valueDay, LedgerEntryType type) {
    public LedgerEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(type, "type");
        if (entryId.isBlank() || sourceEventId.isBlank() || accountId.isBlank()) {
            throw new IllegalArgumentException("Entry, event and account IDs must not be blank");
        }
        if (valueDay < 1) {
            throw new IllegalArgumentException("Business day must be positive");
        }
        int requiredSign = switch (type) {
            case CREDIT -> 1;
            case DEBIT -> -1;
        };
        if (amount.amount().signum() != requiredSign) {
            throw new IllegalArgumentException("Entry sign must agree with entry type");
        }
    }
}
