package ledger.report;

import java.util.List;
import java.util.stream.Collectors;
import ledger.domain.ProcessingError;
import ledger.domain.LedgerEntry;

public record ReplayReport(List<DailyReport> days, List<LedgerEntry> entries,
                           List<ProcessingError> errors) {
    public ReplayReport {
        days = List.copyOf(days);
        entries = List.copyOf(entries);
        errors = List.copyOf(errors);
    }

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
