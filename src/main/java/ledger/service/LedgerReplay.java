package ledger.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.AuthorizationStatus;
import ledger.domain.LedgerValidationException;
import ledger.domain.ProcessingError;
import ledger.domain.LedgerEntry;
import ledger.domain.LedgerEntryType;
import ledger.domain.LedgerCommand;
import ledger.report.DailyReport;
import ledger.report.ReplayReport;

public final class LedgerReplay {
    private final List<Account> accounts;

    public LedgerReplay(List<Account> accounts) {
        this.accounts = List.copyOf(accounts);
    }

    public ReplayReport replay(List<LedgerCommand> commands, int firstDay, int lastDay) {
        if (firstDay < 1 || lastDay < firstDay) {
            throw new IllegalArgumentException("Invalid report period");
        }
        LedgerEngine engine = new LedgerEngine(accounts);
        List<ProcessingError> errors = new ArrayList<>();
        for (LedgerCommand command : commands) {
            try {
                engine.process(command);
            } catch (LedgerValidationException exception) {
                errors.add(new ProcessingError(command.eventId(), command.accountId(), command.postedDay(),
                        exception.code(), exception.getMessage()));
            }
        }
        engine.capitalizeInterest(firstDay, lastDay);

        List<DailyReport> days = new ArrayList<>();
        for (int day = firstDay; day <= lastDay; day++) {
            for (Account account : accounts) {
                int reportDay = day;
                List<LedgerEntry> fees = engine.entries().stream()
                        .filter(entry -> entry.accountId().equals(account.accountId()))
                        .filter(entry -> entry.valueDay() == reportDay)
                        .filter(entry -> entry.type() == LedgerEntryType.OVERDRAFT_FEE
                                || entry.type() == LedgerEntryType.OVERDRAFT_FEE_REVERSAL)
                        .toList();
                Map<String, AuthorizationStatus> states = new LinkedHashMap<>();
                for (Authorization authorization : engine.authorizations()) {
                    if (authorization.accountId().equals(account.accountId())
                            && authorization.createdDay() <= day) {
                        states.put(authorization.authorizationId(), authorization.statusAt(day));
                    }
                }
                List<ProcessingError> dailyErrors = errors.stream()
                        .filter(error -> error.accountId().equals(account.accountId()) && error.day() == reportDay)
                        .toList();
                days.add(new DailyReport(day, account.accountId(), engine.balance(account.accountId(), day),
                        engine.reportedAvailableBalance(account.accountId(), day), fees, states,
                        engine.dailyInterest(account.accountId(), day), dailyErrors));
            }
        }
        return new ReplayReport(days, engine.entries(), errors);
    }
}
