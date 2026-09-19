package ledger.report;

import java.util.List;
import java.util.stream.Collectors;
import ledger.domain.LedgerEntry;

/**
 * Immutable result of a full replay: every account-day projection, the complete
 * append-only entry history, and every captured processing error. Deterministic —
 * the same command list always produces an equal report.
 */
public record ReplayReport(List<DailyReport> days, List<LedgerEntry> entries,
                           List<ProcessingError> errors) {
    public ReplayReport {
        days = List.copyOf(days);
        entries = List.copyOf(entries);
        errors = List.copyOf(errors);
    }

    /** Flat, one-line-per-account-day rendering used by the application's console output. */
    public String render() {
        return days.stream().map(day -> "D" + day.day() + " " + day.accountId()
                + " ledger=" + day.closingLedgerBalance()
                + " available=" + day.availableBalance()
                + " interest=" + day.dailyInterest()
                + " fees=" + day.fees().stream().map(entry -> entry.type().name()).toList()
                + " authorizations=" + day.authorizationStates()
                + " errors=" + day.errors().stream().map(error -> error.code().name()).toList())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    @Override
    public String toString() {
        return render();
    }
}
