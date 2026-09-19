package ledger.service.daily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ledger.domain.Account;
import ledger.domain.enums.Currency;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.enums.LedgerEntryType;
import ledger.service.AccountBalanceCalculator;

/** Assesses AED 25 once per negative closing day; corrections append reversals, never edits. */
public final class OverdraftFeeProcessor {
    private static final Money AED_OVERDRAFT_FEE = Money.of(Currency.AED, "25");

    private final List<LedgerEntry> entries;
    private final AccountBalanceCalculator balances;
    private final Map<String, LedgerEntry> activeFees = new LinkedHashMap<>();
    private final Map<String, Integer> feeCycles = new LinkedHashMap<>();

    public OverdraftFeeProcessor(List<LedgerEntry> entries, AccountBalanceCalculator balances) {
        this.entries = entries;
        this.balances = balances;
    }

    /**
     * Reconciles every day from a movement's value day through the engine's
     * latest processed day — the maximum posted day successfully processed so
     * far. That bound rather than the command's own posted day matters because
     * caller-ordered posted days need not be monotonic.
     */
    public List<LedgerEntry> reconcile(Account account, int firstDay, int lastDay) {
        if (account.currency() != Currency.AED) {
            return List.of();
        }
        String accountId = account.accountId();
        List<LedgerEntry> booked = new ArrayList<>();
        for (int day = Math.max(1, firstDay); day <= lastDay; day++) {
            reconcileDay(accountId, day).ifPresent(booked::add);
        }
        return List.copyOf(booked);
    }

    /**
     * The four-way per-day decision: negative without an active fee books the
     * fee; negative with one is a no-op (once per day); non-negative with an
     * active fee books its reversal at the fee's original value day; otherwise
     * nothing. Evaluated against current entries, so re-running a day is safe.
     */
    private Optional<LedgerEntry> reconcileDay(String accountId, int day) {
        String key = accountId + "/D" + day;
        boolean negative = balances.preFeeBalance(accountId, day).amount().signum() < 0;
        LedgerEntry activeFee = activeFees.get(key);
        if (negative && activeFee == null) {
            int cycle = feeCycles.merge(key, 1, Integer::sum);
            String entryId = "fee:" + key + "/OVERDRAFT" + (cycle == 1 ? "" : ":" + cycle);
            LedgerEntry entry = new LedgerEntry(entryId, entryId, accountId,
                    AED_OVERDRAFT_FEE.negate(), day, LedgerEntryType.OVERDRAFT_FEE, null);
            entries.add(entry);
            activeFees.put(key, entry);
            return Optional.of(entry);
        } else if (!negative && activeFee != null) {
            String entryId = "fee-reversal:" + activeFee.entryId();
            LedgerEntry entry = new LedgerEntry(entryId, entryId, accountId,
                    AED_OVERDRAFT_FEE, activeFee.valueDay(),
                    LedgerEntryType.OVERDRAFT_FEE_REVERSAL, activeFee.entryId());
            entries.add(entry);
            activeFees.remove(key);
            return Optional.of(entry);
        }
        return Optional.empty();
    }
}
