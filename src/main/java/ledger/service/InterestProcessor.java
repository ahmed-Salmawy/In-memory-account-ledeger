package ledger.service;

import java.math.BigDecimal;
import java.util.List;
import ledger.domain.Account;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.LedgerEntryType;

public final class InterestProcessor {
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.0004");

    private final List<Account> accounts;
    private final List<LedgerEntry> entries;
    private final AccountBalanceCalculator balances;

    public InterestProcessor(List<Account> accounts, List<LedgerEntry> entries,
                             AccountBalanceCalculator balances) {
        this.accounts = List.copyOf(accounts);
        this.entries = entries;
        this.balances = balances;
    }

    public Money dailyInterest(String accountId, int day) {
        Money base = balances.balanceWithoutInterest(accountId, day);
        return base.amount().signum() > 0
                ? Money.rounded(base.currency(), base.amount().multiply(DAILY_INTEREST_RATE))
                : Money.rounded(base.currency(), BigDecimal.ZERO);
    }

    public List<LedgerEntry> capitalize(int firstDay, int lastDay) {
        if (firstDay < 1 || lastDay < firstDay) {
            throw new IllegalArgumentException("Invalid interest period");
        }
        List<LedgerEntry> capitalizations = new java.util.ArrayList<>();
        for (Account account : accounts) {
            String entryId = "interest:" + account.accountId() + "/D" + lastDay;
            if (entries.stream().anyMatch(entry -> entry.entryId().equals(entryId))) {
                continue;
            }
            Money total = Money.rounded(account.currency(), BigDecimal.ZERO);
            for (int day = firstDay; day <= lastDay; day++) {
                total = total.add(dailyInterest(account.accountId(), day));
            }
            if (total.amount().signum() > 0) {
                LedgerEntry entry = new LedgerEntry(entryId, entryId, account.accountId(), total,
                        lastDay, LedgerEntryType.INTEREST_CAPITALIZATION, null);
                entries.add(entry);
                capitalizations.add(entry);
            }
        }
        return List.copyOf(capitalizations);
    }
}
