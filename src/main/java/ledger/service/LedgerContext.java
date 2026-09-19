package ledger.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.LedgerEntry;
import ledger.domain.exception.LedgerArgumentException;
import ledger.service.command.LedgerCommandDispatcher;
import ledger.service.command.dto.LedgerCommandPayload;
import ledger.service.command.handler.AuthorizationHandler;
import ledger.service.command.handler.CreditHandler;
import ledger.service.command.handler.DebitHandler;
import ledger.service.command.handler.ReversalHandler;
import ledger.service.command.handler.SettlementHandler;
import ledger.domain.LedgerEntryBook;
import ledger.service.daily.InterestProcessor;
import ledger.service.daily.OverdraftFeeProcessor;

/**
 * One ledger's shared state and the single processor instance built over it —
 * the application context of a {@link LedgerEngine}. Created once per engine
 * and never re-initialized.
 *
 * <p>Holds only state with more than one collaborator; anything a single
 * processor alone needs stays private to that processor.
 *
 * <p>Deliberately not a static instance: every processor closes over these
 * collections, so a JVM-wide context would share one ledger across every replay
 * and test, and determinism would no longer hold.
 */
final class LedgerContext {
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, LedgerCommandPayload> processedCommands = new LinkedHashMap<>();
    private final List<LedgerEntry> ledgerEntries = new ArrayList<>();
    private final Map<String, Authorization> authorizations = new LinkedHashMap<>();
    private final AccountBalanceCalculator accountBalanceCalculator;
    private final LedgerCommandDispatcher ledgerCommandDispatcher;
    private final OverdraftFeeProcessor overdraftFeeProcessor;
    private final InterestProcessor interestProcessor;

    private LedgerContext(List<Account> configuredAccounts) {
        Objects.requireNonNull(configuredAccounts, "accounts");
        for (Account account : configuredAccounts) {
            Objects.requireNonNull(account, "account");
            if (accounts.putIfAbsent(account.accountId(), account) != null) {
                throw new LedgerArgumentException("Duplicate account: " + account.accountId());
            }
        }
        accountBalanceCalculator =
                new AccountBalanceCalculator(accounts, ledgerEntries, authorizations);

        LedgerEntryBook entryBook = new LedgerEntryBook(ledgerEntries);
        ledgerCommandDispatcher = new LedgerCommandDispatcher(List.of(
                new CreditHandler(entryBook),
                new DebitHandler(entryBook),
                new ReversalHandler(entryBook, processedCommands),
                new AuthorizationHandler(authorizations, accountBalanceCalculator),
                new SettlementHandler(authorizations, entryBook)), processedCommands);

        overdraftFeeProcessor = new OverdraftFeeProcessor(ledgerEntries, accountBalanceCalculator);
        interestProcessor = new InterestProcessor(
                List.copyOf(accounts.values()), ledgerEntries, accountBalanceCalculator);
    }

    /** Builds the state and everything that operates on it, once. */
    static LedgerContext create(List<Account> accounts) {
        return new LedgerContext(accounts);
    }

    Map<String, Account> accounts() {
        return accounts;
    }

    Map<String, LedgerCommandPayload> processedCommands() {
        return processedCommands;
    }

    List<LedgerEntry> entries() {
        return ledgerEntries;
    }

    Map<String, Authorization> authorizations() {
        return authorizations;
    }

    AccountBalanceCalculator balances() {
        return accountBalanceCalculator;
    }

    LedgerCommandDispatcher commands() {
        return ledgerCommandDispatcher;
    }

    OverdraftFeeProcessor overdraftFeesProcessor() {
        return overdraftFeeProcessor;
    }

    InterestProcessor interest() {
        return interestProcessor;
    }
}
