package ledger.service;

import java.util.List;
import java.util.Map;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.enums.LedgerEntryType;
import ledger.domain.exception.LedgerValidationException;

import static ledger.domain.enums.AuthorizationStatus.APPROVED;
import static ledger.domain.exception.LedgerValidationException.Code.INVALID_BUSINESS_DAY;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_ACCOUNT;

/**
 * Derives every balance view on demand from the opening balanceCalculator, the
 * append-only entry list, and the live holds — there is no cached or stored
 * balance anywhere; projection is an O(n) scan by design.
 */
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

    /** Unknown accounts are a caller error, surfaced with the stable business code. */
    private Account account(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new LedgerValidationException(UNKNOWN_ACCOUNT, "Unknown account: " + accountId);
        }
        return account;
    }

    /** Everything counts: all booked entries, feesProcessor and interestProcessor, through the given day. */
    public Money balance(String accountId, int day) {
        return balance(accountId, day, true, true, false);
    }

    /** Fee-eligibility view: prior-day feesProcessor count, the day's own fee or reversal does not. */
    public Money preFeeBalance(String accountId, int day) {
        return balance(accountId, day, true, true, true);
    }

    /** Interest excluded — the accrual base, so capitalization never compounds itself. */
    public Money balanceWithoutInterest(String accountId, int day) {
        return balance(accountId, day, true, false, false);
    }

    /** Current view: every live approved hold reduces spendable funds, booked nowhere. */
    public Money availableBalance(String accountId, int day) {
        Money available = balance(accountId, day);
        // O(n) hold scan; index by account if replay size warrants it.
        for (Authorization authorization : authorizations.values()) {
            if (authorization.accountId().equals(accountId) && authorization.status() == APPROVED) {
                available = available.add(authorization.amount().negate());
            }
        }
        return available;
    }

    /** Historical view: only holds that existed on that day, with their status then. */
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

    private Money balance(String accountId, int day, boolean includeFees,
            boolean includeInterest, boolean excludeCurrentDayFees) {
        Account account = account(accountId);
        if (day < 1) {
            throw new LedgerValidationException(INVALID_BUSINESS_DAY,
                    "Business day must be positive");
        }
        Money balance = account.openingBalance();
        // O(n) projection; index by account/value day if replay size warrants it.
        for (LedgerEntry entry : entries) {
            boolean fee = entry.type() == LedgerEntryType.OVERDRAFT_FEE
                    || entry.type() == LedgerEntryType.OVERDRAFT_FEE_REVERSAL;
            boolean interest = entry.type() == LedgerEntryType.INTEREST_CAPITALIZATION;
            boolean currentDayFee = fee && entry.valueDay() == day;
            if (entry.accountId().equals(accountId) && entry.valueDay() <= day
                    && (includeFees || !fee) && (includeInterest || !interest)
                    && !(excludeCurrentDayFees && currentDayFee)) {
                balance = balance.add(entry.amount());
            }
        }
        return balance;
    }
}
