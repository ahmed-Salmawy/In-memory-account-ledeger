package ledger.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ledger.domain.AuthorizationStatus;
import ledger.domain.ProcessingError;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;

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
