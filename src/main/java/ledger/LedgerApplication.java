package ledger;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.Money;
import ledger.service.command.dto.LedgerCommandPayload;
import ledger.report.ReplayReport;
import ledger.service.LedgerReplay;

import static ledger.domain.enums.Currency.AED;
import static ledger.domain.enums.Currency.BHD;
import static ledger.service.command.dto.CommandType.AUTHORIZATION;
import static ledger.service.command.dto.CommandType.CREDIT;
import static ledger.service.command.dto.CommandType.DEBIT;
import static ledger.service.command.dto.CommandType.SETTLEMENT;

/**
 * Runnable entry point for the supplied E1–E10 scenario. This is a library plus
 * a console scenario, not a service: no web layer, storage, or messaging.
 */
public final class LedgerApplication {
    private LedgerApplication() {
    }

    /** Builds the supplied Day 1–6 accounts and command stream and replays it. */
    public static ReplayReport run() {
        List<Account> accounts = List.of(
                new Account("ACC-001", AED, Money.of(AED, "0")),
                new Account("ACC-002", BHD, Money.of(BHD, "0")));
        List<LedgerCommandPayload> commands = List.of(
                new LedgerCommandPayload("E1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1200")),
                new LedgerCommandPayload("E2", 1, 1, DEBIT, "ACC-001", Money.of(AED, "950")),
                new LedgerCommandPayload("E3", 2, 2, AUTHORIZATION, "ACC-001", Money.of(AED, "200"), "Auth-A"),
                new LedgerCommandPayload("E4", 3, 3, CREDIT, "ACC-001", Money.of(AED, "400")),
                new LedgerCommandPayload("E5", 4, 4, SETTLEMENT, "ACC-001", Money.of(AED, "185"), "Auth-A"),
                new LedgerCommandPayload("E6", 4, 4, SETTLEMENT, "ACC-001", Money.of(AED, "180"), "Auth-Z"),
                new LedgerCommandPayload("E7", 5, 2, DEBIT, "ACC-001", Money.of(AED, "620")),
                new LedgerCommandPayload("E8", 5, 5, AUTHORIZATION, "ACC-001", Money.of(AED, "90"), "Auth-B"),
                LedgerCommandPayload.reversal("E9", 6, 2, "ACC-001", "E7"),
                LedgerCommandPayload.allocatedCredit("E10", 5, 5, "ACC-002", Money.of(BHD, "10"), 3));
        return new LedgerReplay(accounts).replay(commands, 1, 6);
    }

    /** Prints the rendered Day 1–6 report to standard output. */
    public static void main(String[] args) {
        System.out.println(run().render());
    }
}
