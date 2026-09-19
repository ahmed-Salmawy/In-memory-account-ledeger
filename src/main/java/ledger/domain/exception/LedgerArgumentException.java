package ledger.domain.exception;

/** A caller error in domain input: malformed identifiers, days, amounts, or signs. */
public final class LedgerArgumentException extends RuntimeException {
    public LedgerArgumentException(String message) {
        super(message);
    }
}
