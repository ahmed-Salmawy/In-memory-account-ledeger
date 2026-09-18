package ledger.service;

import java.util.List;
import java.util.Map;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.LedgerEntryType;
import ledger.domain.LedgerValidationException;

import static ledger.domain.AuthorizationStatus.APPROVED;
import static ledger.domain.LedgerValidationException.Code.UNKNOWN_ACCOUNT;

public final class AccountBalanceCalculator {
    private final Map<String, Account> accounts;
    private final List<LedgerEntry> entries;
    private final Map<String, Authorization> authorizations;

    public AccountBalanceCalculator(Map<String, Account> accounts, List<LedgerEntry> entries,
                                    Map<String, Authorization> authorizations) {
        this.accounts = accounts;
        this.entries = entries;
        this.authorizations = authorizations;
    }

    public Account account(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new LedgerValidationException(UNKNOWN_ACCOUNT, "Unknown account: " + accountId);
        }
        return account;
    }

    public Money balance(String accountId, int day) {
        return balance(accountId, day, true, true);
    }

    public Money balanceWithoutFees(String accountId, int day) {
        return balance(accountId, day, false, true);
    }

    public Money balanceWithoutInterest(String accountId, int day) {
        return balance(accountId, day, true, false);
    }

    public Money availableBalance(String accountId, int day) {
        Money available = balance(accountId, day);
        // ponytail: O(n) hold scan; index by account if replay size warrants it.
        for (Authorization authorization : authorizations.values()) {
            if (authorization.accountId().equals(accountId) && authorization.status() == APPROVED) {
                available = available.add(authorization.amount().negate());
            }
        }
        return available;
    }

    public Money reportedAvailableBalance(String accountId, int day) {
        Money available = balance(accountId, day);
        for (Authorization authorization : authorizations.values()) {
            if (authorization.accountId().equals(accountId) && authorization.createdDay() <= day
                    && authorization.statusAt(day) == APPROVED) {
                available = available.add(authorization.amount().negate());
            }
        }
        return available;
    }

    private Money balance(String accountId, int day, boolean includeFees, boolean includeInterest) {
        Account account = account(accountId);
        if (day < 1) {
            throw new IllegalArgumentException("Business day must be positive");
        }
        Money balance = account.openingBalance();
        // ponytail: O(n) projection; index by account/value day if replay size warrants it.
        for (LedgerEntry entry : entries) {
            boolean fee = entry.type() == LedgerEntryType.OVERDRAFT_FEE
                    || entry.type() == LedgerEntryType.OVERDRAFT_FEE_REVERSAL;
            boolean interest = entry.type() == LedgerEntryType.INTEREST_CAPITALIZATION;
            if (entry.accountId().equals(accountId) && entry.valueDay() <= day
                    && (includeFees || !fee) && (includeInterest || !interest)) {
                balance = balance.add(entry.amount());
            }
        }
        return balance;
    }
}
