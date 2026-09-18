package ledger;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.Money;
import ledger.domain.LedgerCommand;
import ledger.report.ReplayReport;
import ledger.service.LedgerReplay;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static ledger.domain.CommandType.*;

public final class LedgerApplication {
    private LedgerApplication() {
    }

    public static ReplayReport run() {
        List<Account> accounts = List.of(
                new Account("ACC-001", Money.of(AED, "0")),
                new Account("ACC-002", Money.of(BHD, "0")));
        List<LedgerCommand> commands = List.of(
                new LedgerCommand("E1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1200")),
                new LedgerCommand("E2", 1, 1, DEBIT, "ACC-001", Money.of(AED, "950")),
                new LedgerCommand("E3", 2, 2, AUTHORIZATION, "ACC-001", Money.of(AED, "200"), "Auth-A"),
                new LedgerCommand("E4", 3, 3, CREDIT, "ACC-001", Money.of(AED, "400")),
                new LedgerCommand("E5", 4, 4, SETTLEMENT, "ACC-001", Money.of(AED, "185"), "Auth-A"),
                new LedgerCommand("E6", 4, 4, SETTLEMENT, "ACC-001", Money.of(AED, "180"), "Auth-Z"),
                new LedgerCommand("E7", 5, 2, DEBIT, "ACC-001", Money.of(AED, "620")),
                new LedgerCommand("E8", 5, 5, AUTHORIZATION, "ACC-001", Money.of(AED, "90"), "Auth-B"),
                LedgerCommand.reversal("E9", 6, 2, "ACC-001", "E7"),
                LedgerCommand.allocatedCredit("E10", 5, 5, "ACC-002", Money.of(BHD, "10"), 3));
        return new LedgerReplay(accounts).replay(commands, 1, 6);
    }

    public static void main(String[] args) {
        System.out.println(run().render());
    }
}
