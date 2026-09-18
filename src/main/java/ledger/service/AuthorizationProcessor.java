package ledger.service;

import java.util.Map;
import ledger.domain.Authorization;
import ledger.domain.AuthorizationStatus;
import ledger.domain.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.LedgerCommand;
import ledger.domain.LedgerEntry;

import static ledger.domain.AuthorizationStatus.APPROVED;
import static ledger.domain.AuthorizationStatus.DECLINED;
import static ledger.domain.AuthorizationStatus.SETTLED;
import static ledger.domain.LedgerValidationException.Code.AUTHORIZATION_ACCOUNT_MISMATCH;
import static ledger.domain.LedgerValidationException.Code.AUTHORIZATION_NOT_APPROVED;
import static ledger.domain.LedgerValidationException.Code.DUPLICATE_AUTHORIZATION_ID;
import static ledger.domain.LedgerValidationException.Code.SETTLEMENT_EXCEEDS_AUTHORIZATION;
import static ledger.domain.LedgerValidationException.Code.UNKNOWN_AUTHORIZATION;

public final class AuthorizationProcessor {
    private final Map<String, Authorization> authorizations;
    private final AccountBalanceCalculator balances;
    private final TransactionProcessor transactions;

    public AuthorizationProcessor(Map<String, Authorization> authorizations,
                                  AccountBalanceCalculator balances,
                                  TransactionProcessor transactions) {
        this.authorizations = authorizations;
        this.balances = balances;
        this.transactions = transactions;
    }

    public Authorization authorize(LedgerCommand command) {
        if (authorizations.containsKey(command.authorizationId())) {
            throw new LedgerValidationException(DUPLICATE_AUTHORIZATION_ID,
                    "Duplicate authorization ID: " + command.authorizationId());
        }
        Money remaining = balances.availableBalance(command.accountId(), command.postedDay())
                .add(command.amount().negate());
        AuthorizationStatus status = remaining.amount().signum() >= 0 ? APPROVED : DECLINED;
        Authorization authorization = new Authorization(command.authorizationId(),
                command.accountId(), command.amount(), command.postedDay(), status);
        authorizations.put(command.authorizationId(), authorization);
        return authorization;
    }

    public SettlementResult settle(LedgerCommand command) {
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
        LedgerEntry entry = transactions.settlement(command);
        authorizations.put(authorization.authorizationId(), settled);
        return new SettlementResult(settled, entry);
    }

    public record SettlementResult(Authorization authorization, LedgerEntry entry) {
    }
}
