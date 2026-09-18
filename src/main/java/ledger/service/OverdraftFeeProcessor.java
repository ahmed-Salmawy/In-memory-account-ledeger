package ledger.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ledger.domain.Account;
import ledger.domain.Currency;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.LedgerEntryType;

public final class OverdraftFeeProcessor {
    private static final Money AED_OVERDRAFT_FEE = Money.of(Currency.AED, "25");

    private final List<LedgerEntry> entries;
    private final AccountBalanceCalculator balances;
    private final Map<String, String> activeFees = new LinkedHashMap<>();
    private final Map<String, Integer> feeCycles = new LinkedHashMap<>();

    public OverdraftFeeProcessor(List<LedgerEntry> entries, AccountBalanceCalculator balances) {
        this.entries = entries;
        this.balances = balances;
    }

    public Optional<LedgerEntry> reconcile(String accountId, int day) {
        Account account = balances.account(accountId);
        if (account.currency() != Currency.AED) {
            return Optional.empty();
        }
        String key = accountId + "/D" + day;
        boolean negative = balances.balanceWithoutFees(accountId, day).amount().signum() < 0;
        String activeFeeId = activeFees.get(key);
        if (negative && activeFeeId == null) {
            int cycle = feeCycles.merge(key, 1, Integer::sum);
            String entryId = "fee:" + key + "/OVERDRAFT" + (cycle == 1 ? "" : ":" + cycle);
            LedgerEntry entry = new LedgerEntry(entryId, entryId, accountId,
                    AED_OVERDRAFT_FEE.negate(), day, LedgerEntryType.OVERDRAFT_FEE, null);
            entries.add(entry);
            activeFees.put(key, entryId);
            return Optional.of(entry);
        } else if (!negative && activeFeeId != null) {
            String entryId = "fee-reversal:" + activeFeeId;
            LedgerEntry entry = new LedgerEntry(entryId, entryId, accountId,
                    AED_OVERDRAFT_FEE, day, LedgerEntryType.OVERDRAFT_FEE_REVERSAL, activeFeeId);
            entries.add(entry);
            activeFees.remove(key);
            return Optional.of(entry);
        }
        return Optional.empty();
    }
}
