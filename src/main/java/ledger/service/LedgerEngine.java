package ledger.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.LedgerDomainEvent;
import ledger.domain.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.CommandType;
import ledger.domain.LedgerCommand;
import ledger.domain.LedgerEntry;

import static ledger.domain.LedgerValidationException.Code.CONFLICTING_EVENT_ID;
import static ledger.domain.LedgerValidationException.Code.CURRENCY_MISMATCH;

/** Single-threaded orchestration in caller order; behavior lives in focused processors. */
public final class LedgerEngine {
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, LedgerCommand> processedCommands = new LinkedHashMap<>();
    private final List<LedgerEntry> entries = new ArrayList<>();
    private final Map<String, Authorization> authorizations = new LinkedHashMap<>();
    private final AccountBalanceCalculator balances;
    private final TransactionProcessor transactions;
    private final AuthorizationProcessor authorizationProcessor;
    private final OverdraftFeeProcessor fees;
    private final InterestProcessor interest;
    private final InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();

    public LedgerEngine(List<Account> accounts) {
        for (Account account : accounts) {
            if (this.accounts.putIfAbsent(account.accountId(), account) != null) {
                throw new IllegalArgumentException("Duplicate account: " + account.accountId());
            }
        }
        balances = new AccountBalanceCalculator(this.accounts, entries, authorizations);
        transactions = new TransactionProcessor(entries, processedCommands);
        authorizationProcessor = new AuthorizationProcessor(authorizations, balances, transactions);
        fees = new OverdraftFeeProcessor(entries, balances);
        interest = new InterestProcessor(List.copyOf(this.accounts.values()), entries, balances);
        eventPublisher.subscribe(LedgerDomainEvent.MovementProcessed.class, movement ->
                fees.reconcile(movement.command().accountId(), movement.command().valueDay())
                        .ifPresent(entry -> eventPublisher.publish(
                                new LedgerDomainEvent.OverdraftFeeProcessed(entry))));
    }

    public void process(LedgerCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            processValid(command);
        } catch (LedgerValidationException exception) {
            eventPublisher.publish(new LedgerDomainEvent.ProcessingRejected(
                    command, exception.code(), exception.getMessage()));
            throw exception;
        }
    }

    private void processValid(LedgerCommand command) {
        LedgerCommand previous = processedCommands.get(command.eventId());
        if (previous != null) {
            if (!previous.equals(command)) {
                throw new LedgerValidationException(CONFLICTING_EVENT_ID,
                        "Conflicting event ID: " + command.eventId());
            }
            return;
        }
        Account account = balances.account(command.accountId());
        if (command.type() != CommandType.REVERSAL && account.currency() != command.amount().currency()) {
            throw new LedgerValidationException(CURRENCY_MISMATCH,
                    "Command currency differs from account currency");
        }
        LedgerDomainEvent processed = switch (command.type()) {
            case CREDIT -> new LedgerDomainEvent.CreditProcessed(
                    command, transactions.credit(command));
            case DEBIT -> new LedgerDomainEvent.DebitProcessed(
                    command, List.of(transactions.debit(command)));
            case AUTHORIZATION -> new LedgerDomainEvent.AuthorizationProcessed(
                    command, authorizationProcessor.authorize(command));
            case SETTLEMENT -> {
                AuthorizationProcessor.SettlementResult result = authorizationProcessor.settle(command);
                yield new LedgerDomainEvent.SettlementProcessed(
                        command, List.of(result.entry()), result.authorization());
            }
            case REVERSAL -> new LedgerDomainEvent.ReversalProcessed(
                    command, transactions.reverse(command));
        };
        processedCommands.put(command.eventId(), command);
        eventPublisher.publish(processed);
    }

    public Money balance(String accountId, int day) {
        return balances.balance(accountId, day);
    }

    public Money availableBalance(String accountId, int day) {
        return balances.availableBalance(accountId, day);
    }

    public Money reportedAvailableBalance(String accountId, int day) {
        return balances.reportedAvailableBalance(accountId, day);
    }

    public Money dailyInterest(String accountId, int day) {
        return interest.dailyInterest(accountId, day);
    }

    public void capitalizeInterest(int firstDay, int lastDay) {
        interest.capitalize(firstDay, lastDay).forEach(entry ->
                eventPublisher.publish(new LedgerDomainEvent.InterestCapitalized(entry)));
    }

    public <E extends LedgerDomainEvent> void on(Class<E> eventType,
                                                  Consumer<? super E> subscriber) {
        eventPublisher.subscribe(eventType, subscriber);
    }

    public List<InMemoryEventPublisher.DeliveryFailure> eventDeliveryFailures() {
        return eventPublisher.failures();
    }

    public List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }

    public List<Authorization> authorizations() {
        return List.copyOf(authorizations.values());
    }

    public List<Account> accounts() {
        return List.copyOf(accounts.values());
    }
}
