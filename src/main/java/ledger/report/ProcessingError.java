package ledger.report;

import java.util.Objects;
import ledger.domain.exception.LedgerValidationException;

/** A business rejection captured during replay — the command, where it landed, and why. */
public record ProcessingError(String eventId, String accountId, int day,
                              LedgerValidationException.Code code, String message) {
    public ProcessingError {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
