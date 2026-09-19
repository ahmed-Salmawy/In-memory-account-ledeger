package ledger.service;

import java.util.List;
import java.util.Objects;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.LedgerEntry;
import ledger.domain.Money;
import ledger.domain.exception.LedgerArgumentException;
import ledger.domain.exception.LedgerValidationException;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;

import static ledger.domain.exception.LedgerValidationException.Code.CONFLICTING_EVENT_ID;
import static ledger.domain.exception.LedgerValidationException.Code.CURRENCY_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_ACCOUNT;

/**
 * Single-threaded orchestration in caller order; behavior lives in focused processors.
 */
public final class LedgerEngine {
    private final LedgerContext context;
    private int latestProcessedDay;

    /**
     * Builds this engine's context — its state and one instance of each
     * processor. A duplicate account is a caller error.
     */
    public LedgerEngine(List<Account> accounts) {
        context = LedgerContext.create(accounts);
    }

    /**
     * Handles one command in caller order. Business rejections are thrown —
     * callers decide whether to abort a replay or record the error and continue.
     */
    public void process(LedgerCommandPayload command) {
        Objects.requireNonNull(command, "command");
        try {
            validate(command);
            List<LedgerEntry> booked = context.commands().dispatch(command);
            int previousLatestDay = latestProcessedDay;
            latestProcessedDay = Math.max(latestProcessedDay, command.postedDay());
            if (!booked.isEmpty()) {
                reconcileFees(account(command.accountId()), command.valueDay(), latestProcessedDay);
            }
            assessNewlyReachedDays(previousLatestDay);

        } catch (IdenticalRetryException ignored) {
        }
    }


    /**
     * Checks event ID conflicts, account existence, and currency compatibility.
     * Identical retries raise a private signal that stops processing without
     * publishing a business rejection.
     */
    private void validate(LedgerCommandPayload command) {
        LedgerCommandPayload previous = context.processedCommands().get(command.eventId());
        if (previous != null) {
            if (previous.equals(command)) {
                throw new IdenticalRetryException();
            }
            throw new LedgerValidationException(CONFLICTING_EVENT_ID,
                    "Conflicting event ID: " + command.eventId());
        }

        Account account = account(command.accountId());
        if (command.type() != CommandType.REVERSAL && account.currency() != command.amount().currency()) {
            throw new LedgerValidationException(CURRENCY_MISMATCH,
                    "Command currency differs from account currency");
        }
    }

    private Account account(String accountId) {
        Account account = context.accounts().get(accountId);
        if (account == null) {
            throw new LedgerValidationException(UNKNOWN_ACCOUNT, "Unknown account: " + accountId);
        }
        return account;
    }

    /**
     * The single fee path: book whatever reconciliation finds for the span. A
     * movement reconciles from its value day; a newly reached day reconciles
     * only itself.
     */
    private void reconcileFees(Account account, int firstDay, int lastDay) {
        context.overdraftFeesProcessor().reconcile(account, firstDay, lastDay);
    }

    /**
     * A day can become fee-bearing even when a command creates no ledger movement.
     */
    private void assessNewlyReachedDays(int previousLatestDay) {
        for (int day = previousLatestDay + 1; day <= latestProcessedDay; day++) {
            for (Account account : context.accounts().values()) {
                reconcileFees(account, day, day);
            }
        }
    }

    /**
     * Extends fee assessment through a report's final day, past the last command.
     */
    public void assessFeesThrough(int day) {
        if (day < 1) {
            throw new LedgerArgumentException("Business day must be positive");
        }
        int previousLatestDay = latestProcessedDay;
        latestProcessedDay = Math.max(latestProcessedDay, day);
        assessNewlyReachedDays(previousLatestDay);
    }

    /**
     * Closing ledger balance: opening plus every known entry with valueDay ≤ day.
     */
    public Money balance(String accountId, int day) {
        return context.balances().balance(accountId, day);
    }

    /**
     * Ledger balance minus all currently approved holds — spendable funds, never stored.
     */
    public Money availableBalance(String accountId, int day) {
        return context.balances().availableBalance(accountId, day);
    }

    /**
     * Available balance as it looked on that historical day, using each hold's status then.
     */
    public Money reportedAvailableBalance(String accountId, int day) {
        return context.balances().reportedAvailableBalance(accountId, day);
    }

    /**
     * One day's interest accrual: positive fee-inclusive balances only, rounded HALF_EVEN.
     */
    public Money dailyInterest(String accountId, int day) {
        return context.interest().dailyInterest(accountId, day);
    }

    /**
     * Appends one capitalization entry per account — the sum of its rounded daily accruals.
     */
    public void capitalizeInterest(int firstDay, int lastDay) {
        context.interest().capitalize(firstDay, lastDay);
    }

    /**
     * Immutable snapshot of the full append-only history, in booking order.
     */
    public List<LedgerEntry> entries() {
        return List.copyOf(context.entries());
    }

    /**
     * Immutable snapshot of every authorization attempt, in processing order.
     */
    public List<Authorization> authorizations() {
        return List.copyOf(context.authorizations().values());
    }

    private static final class IdenticalRetryException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

}
