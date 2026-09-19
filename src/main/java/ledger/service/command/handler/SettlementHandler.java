package ledger.service.command.handler;

import java.util.List;
import java.util.Map;
import ledger.domain.Authorization;
import ledger.domain.LedgerEntry;
import ledger.domain.exception.LedgerValidationException;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;
import ledger.service.command.LedgerEntryBook;
import ledger.domain.enums.LedgerEntryType;

import static ledger.domain.enums.AuthorizationStatus.APPROVED;
import static ledger.domain.enums.AuthorizationStatus.SETTLED;
import static ledger.domain.exception.LedgerValidationException.Code.AUTHORIZATION_ACCOUNT_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.AUTHORIZATION_NOT_APPROVED;
import static ledger.domain.exception.LedgerValidationException.Code.SETTLEMENT_EXCEEDS_AUTHORIZATION;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_AUTHORIZATION;

/** Converts an approved hold into one booked debit and releases the entire hold. */
public final class SettlementHandler implements LedgerCommandHandler {
    private final Map<String, Authorization> authorizations;
    private final LedgerEntryBook entryBook;

    public SettlementHandler(Map<String, Authorization> authorizations, LedgerEntryBook entryBook) {
        this.authorizations = authorizations;
        this.entryBook = entryBook;
    }

    @Override
    public CommandType handles() {
        return CommandType.SETTLEMENT;
    }

    /**
     * Validates the hold (exists, same account, approved, no over-capture) before
     * booking the debit, then marks the authorization SETTLED — releasing the full
     * hold even though only part of it may have been captured.
     */
    @Override
    public List<LedgerEntry> handle(LedgerCommandPayload command) {
        Authorization authorization = authorizations.get(command.authorizationId());
        if (authorization == null) {
            throw new LedgerValidationException(UNKNOWN_AUTHORIZATION,
                    "Unknown authorization: " + command.authorizationId());
        }
        if (!authorization.accountId().equals(command.accountId())) {
            throw new LedgerValidationException(AUTHORIZATION_ACCOUNT_MISMATCH,
                    "Authorization belongs to another account: " + command.authorizationId());
        }
        if (authorization.status() != APPROVED) {
            throw new LedgerValidationException(AUTHORIZATION_NOT_APPROVED,
                    "Authorization is " + authorization.status() + ": " + command.authorizationId());
        }
        if (command.amount().amount().compareTo(authorization.amount().amount()) > 0) {
            throw new LedgerValidationException(SETTLEMENT_EXCEEDS_AUTHORIZATION,
                    "Settlement exceeds authorized amount: " + command.authorizationId());
        }
        Authorization settled = new Authorization(authorization.authorizationId(), authorization.accountId(),
                authorization.amount(), authorization.createdDay(), SETTLED, command.postedDay());
        LedgerEntry entry = entryBook.post(command, LedgerEntryType.SETTLEMENT,
                command.amount().negate(), command.authorizationId(), 1, 1);
        authorizations.put(authorization.authorizationId(), settled);
        return List.of(entry);
    }
}
