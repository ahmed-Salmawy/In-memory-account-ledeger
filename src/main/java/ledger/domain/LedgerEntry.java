package ledger.domain;

import java.util.Objects;
import ledger.domain.enums.LedgerEntryType;
import ledger.domain.exception.LedgerArgumentException;

/**
 * One immutable, append-only booked movement — the ledger's unit of record.
 * Sign, reference and identifiers are validated at construction; corrections
 * are new entries that reference their source, never edits.
 */
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
            throw new LedgerArgumentException("Entry, event and account IDs must not be blank");
        }
        if (valueDay < 1) {
            throw new LedgerArgumentException("Business day must be positive");
        }
        int requiredSign = switch (type) {
            case CREDIT, OVERDRAFT_FEE_REVERSAL, INTEREST_CAPITALIZATION -> 1;
            case DEBIT, SETTLEMENT, OVERDRAFT_FEE -> -1;
            case REVERSAL -> amount.amount().signum();
        };
        if (requiredSign == 0 || amount.amount().signum() != requiredSign) {
            throw new LedgerArgumentException("Entry sign must agree with entry type");
        }
        if (type == LedgerEntryType.SETTLEMENT || type == LedgerEntryType.REVERSAL
                || type == LedgerEntryType.OVERDRAFT_FEE_REVERSAL) {
            if (referenceId == null || referenceId.isBlank()) {
                throw new LedgerArgumentException(type + " entry must carry a reference");
            }
        } else if (referenceId != null) {
            throw new LedgerArgumentException("Entry type may not carry a reference");
        }
    }

    /** The hold a settlement entry consumed, or null for every other entry type. */
    public String authorizationId() {
        return type == LedgerEntryType.SETTLEMENT ? referenceId : null;
    }
}
