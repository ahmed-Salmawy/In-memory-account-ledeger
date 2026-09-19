package ledger.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ledger.domain.enums.AuthorizationStatus;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;

/**
 * One account's projection for one business day: closing ledger balance,
 * available balance (hold-aware), fee entries landing that day, authorization
 * states as of that day, the day's interest accrual, and that day's rejections.
 */
public record DailyReport(int day, String accountId, Money closingLedgerBalance,
                          Money availableBalance, List<LedgerEntry> fees,
                          Map<String, AuthorizationStatus> authorizationStates,
                          Money dailyInterest, List<ProcessingError> errors) {
    public DailyReport {
        fees = List.copyOf(fees);
        authorizationStates = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(authorizationStates));
        errors = List.copyOf(errors);
    }
}
