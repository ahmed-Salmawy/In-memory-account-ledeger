package ledger.domain;

import java.util.Objects;

public record ProcessingError(String eventId, String accountId, int day,
                              LedgerValidationException.Code code, String message) {
    public ProcessingError {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
