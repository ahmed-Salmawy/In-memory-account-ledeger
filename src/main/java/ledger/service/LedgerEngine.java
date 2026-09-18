package ledger.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ledger.domain.Account;
import ledger.domain.LedgerEntry;
import ledger.domain.LedgerEntryType;
import ledger.domain.LedgerEvent;
import ledger.domain.Money;

/** Single-threaded posting in caller order, with balances derived on demand. */
public final class LedgerEngine {
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, LedgerEvent> processedEvents = new HashMap<>();
    private final List<LedgerEntry> entries = new ArrayList<>();

    public LedgerEngine(List<Account> accounts) {
        for (Account account : accounts) {
            if (this.accounts.putIfAbsent(account.accountId(), account) != null) {
                throw new IllegalArgumentException("Duplicate account: " + account.accountId());
            }
        }
    }

    public void process(LedgerEvent event) {
        Objects.requireNonNull(event, "event");
        LedgerEvent previous = processedEvents.get(event.eventId());
        if (previous != null) {
            if (!previous.equals(event)) {
                throw new IllegalArgumentException("Conflicting event ID: " + event.eventId());
            }
            return;
        }
        Account account = account(event.accountId());
        if (account.currency() != event.amount().currency()) {
            throw new IllegalArgumentException("Event currency differs from account currency");
        }
        LedgerEntryType type = switch (event.type()) {
            case CREDIT -> LedgerEntryType.CREDIT;
            case DEBIT -> LedgerEntryType.DEBIT;
        };
        Money signedAmount = switch (event.type()) {
            case CREDIT -> event.amount();
            case DEBIT -> event.amount().negate();
        };
        LedgerEntry entry = new LedgerEntry("event:" + event.eventId(), event.eventId(),
                event.accountId(), signedAmount, event.valueDay(), type);
        entries.add(entry);
        processedEvents.put(event.eventId(), event);
    }

    /** Projects currently known entries by value day, not by posted-day visibility. */
    public Money balance(String accountId, int day) {
        Account account = account(accountId);
        if (day < 1) {
            throw new IllegalArgumentException("Business day must be positive");
        }
        Money balance = account.openingBalance();
        // ponytail: O(n) projection; index by account/value day if replay size warrants it.
        for (LedgerEntry entry : entries) {
            if (entry.accountId().equals(accountId) && entry.valueDay() <= day) {
                balance = balance.add(entry.amount());
            }
        }
        return balance;
    }

    public List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }

    private Account account(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Unknown account: " + accountId);
        }
        return account;
    }
}
