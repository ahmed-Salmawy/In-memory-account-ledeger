package ledger.domain.enums;

/** Hold lifecycle: reserves reduce available funds but are never booked money. */
public enum AuthorizationStatus {
    /** Active hold — subtracted from available balance until settled. */
    APPROVED,
    /** Retained rejection — kept for audit; reserves nothing. */
    DECLINED,
    /** Converted to a booked debit; the full hold is released. */
    SETTLED
}
