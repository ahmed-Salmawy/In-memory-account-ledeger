package ledger.service.command.handler;

import java.util.List;
import java.util.Map;
import ledger.domain.Authorization;
import ledger.domain.LedgerEntry;
import ledger.domain.Money;
import ledger.domain.enums.AuthorizationStatus;
import ledger.domain.exception.LedgerValidationException;
import ledger.service.AccountBalanceCalculator;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;

import static ledger.domain.enums.AuthorizationStatus.APPROVED;
import static ledger.domain.enums.AuthorizationStatus.DECLINED;
import static ledger.domain.exception.LedgerValidationException.Code.DUPLICATE_AUTHORIZATION_ID;

/**
 * Reserves spending capacity without booking money. Declined attempts are
 * retained for audit. Books no entries — the empty result is what keeps a hold
 * from ever triggering an overdraft fee.
 */
public final class AuthorizationHandler implements LedgerCommandHandler {
    private final Map<String, Authorization> authorizations;
    private final AccountBalanceCalculator balances;

    public AuthorizationHandler(Map<String, Authorization> authorizations,
                                AccountBalanceCalculator balances) {
        this.authorizations = authorizations;
        this.balances = balances;
    }

    @Override
    public CommandType handles() {
        return CommandType.AUTHORIZATION;
    }

    /**
     * Approves only if available funds through the request's posted day minus
     * every current approved hold stay non-negative — exactly sufficient funds
     * approve. The decision is made once and never rewritten by later movements.
     */
    @Override
    public List<LedgerEntry> handle(LedgerCommandPayload command) {
        if (authorizations.containsKey(command.authorizationId())) {
            throw new LedgerValidationException(DUPLICATE_AUTHORIZATION_ID,
                    "Duplicate authorization ID: " + command.authorizationId());
        }
        Money remaining = balances.availableBalance(command.accountId(), command.postedDay())
                .add(command.amount().negate());
        AuthorizationStatus status = remaining.amount().signum() >= 0 ? APPROVED : DECLINED;
        authorizations.put(command.authorizationId(), new Authorization(command.authorizationId(),
                command.accountId(), command.amount(), command.postedDay(), status));
        return List.of();
    }
}
