package ledger.domain;

public enum LedgerEntryType {
    CREDIT,
    DEBIT,
    SETTLEMENT,
    REVERSAL,
    OVERDRAFT_FEE,
    OVERDRAFT_FEE_REVERSAL,
    INTEREST_CAPITALIZATION
}
