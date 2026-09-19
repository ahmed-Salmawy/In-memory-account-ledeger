package ledger.domain.enums;

/**
 * Booked entry kinds with their mandatory signs: CREDIT, FEE_REVERSAL and
 * INTEREST_CAPITALIZATION book positive; DEBIT, SETTLEMENT and OVERDRAFT_FEE
 * book negative; REVERSAL carries the opposite sign of its source entry.
 */
public enum LedgerEntryType {
    CREDIT,
    DEBIT,
    SETTLEMENT,
    REVERSAL,
    OVERDRAFT_FEE,
    OVERDRAFT_FEE_REVERSAL,
    INTEREST_CAPITALIZATION
}
