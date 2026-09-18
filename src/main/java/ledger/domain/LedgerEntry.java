package ledger.domain;

import java.util.Objects;

public record LedgerEntry(String entryId, String sourceEventId, String accountId,
                          Money amount, int valueDay, LedgerEntryType type, String referenceId) {
    public LedgerEntry(String entryId, String sourceEventId, String accountId,
                       Money amount, int valueDay, LedgerEntryType type) {
        this(entryId, sourceEventId, accountId, amount, valueDay, type, null);
    }

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
            case CREDIT, OVERDRAFT_FEE_REVERSAL, INTEREST_CAPITALIZATION -> 1;
            case DEBIT, SETTLEMENT, OVERDRAFT_FEE -> -1;
            case REVERSAL -> amount.amount().signum();
        };
        if (requiredSign == 0 || amount.amount().signum() != requiredSign) {
            throw new IllegalArgumentException("Entry sign must agree with entry type");
        }
        if (type == LedgerEntryType.SETTLEMENT || type == LedgerEntryType.REVERSAL
                || type == LedgerEntryType.OVERDRAFT_FEE_REVERSAL) {
            if (referenceId == null || referenceId.isBlank()) {
                throw new IllegalArgumentException(type + " entry must carry a reference");
            }
        } else if (referenceId != null) {
            throw new IllegalArgumentException("Entry type may not carry a reference");
        }
    }

    public String authorizationId() {
        return type == LedgerEntryType.SETTLEMENT ? referenceId : null;
    }
}
