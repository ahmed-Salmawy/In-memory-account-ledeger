package ledger.domain;

import java.util.Objects;

/** A business rejection identified by a stable code rather than message text. */
public final class LedgerValidationException extends RuntimeException {
    public enum Code {
        UNKNOWN_ACCOUNT,
        CONFLICTING_EVENT_ID,
        CURRENCY_MISMATCH,
        DUPLICATE_AUTHORIZATION_ID,
        UNKNOWN_AUTHORIZATION,
        AUTHORIZATION_ACCOUNT_MISMATCH,
        AUTHORIZATION_NOT_APPROVED,
        SETTLEMENT_EXCEEDS_AUTHORIZATION,
        UNKNOWN_EVENT,
        EVENT_ACCOUNT_MISMATCH,
        EVENT_ALREADY_REVERSED,
        EVENT_NOT_REVERSIBLE
    }

    private final Code code;

    public LedgerValidationException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
