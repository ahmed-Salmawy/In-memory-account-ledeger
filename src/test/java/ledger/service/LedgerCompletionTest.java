package ledger.service;

import java.util.List;
import ledger.LedgerApplication;
import ledger.domain.Account;
import ledger.domain.AuthorizationStatus;
import ledger.domain.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.LedgerEntryType;
import ledger.domain.LedgerCommand;
import ledger.report.DailyReport;
import ledger.report.ReplayReport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static ledger.domain.LedgerValidationException.Code.EVENT_ACCOUNT_MISMATCH;
import static ledger.domain.LedgerValidationException.Code.EVENT_ALREADY_REVERSED;
import static ledger.domain.LedgerValidationException.Code.EVENT_NOT_REVERSIBLE;
import static ledger.domain.LedgerValidationException.Code.UNKNOWN_AUTHORIZATION;
import static ledger.domain.LedgerValidationException.Code.UNKNOWN_EVENT;
import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static ledger.domain.CommandType.AUTHORIZATION;
import static ledger.domain.CommandType.CREDIT;
import static ledger.domain.CommandType.DEBIT;
import static ledger.domain.LedgerEntryType.INTEREST_CAPITALIZATION;
import static ledger.domain.LedgerEntryType.OVERDRAFT_FEE;
import static ledger.domain.LedgerEntryType.OVERDRAFT_FEE_REVERSAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerCompletionTest {
    @Test
    void reversalAppendsCompensationAndReconcilesOnlyItsValueDayFee() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("A", Money.of(AED, "0"))));
        engine.process(new LedgerCommand("C1", 1, 1, CREDIT, "A", Money.of(AED, "100")));
        engine.process(new LedgerCommand("D1", 5, 2, DEBIT, "A", Money.of(AED, "150")));
        assertEquals(Money.of(AED, "-75"), engine.balance("A", 2));
        assertEquals(List.of(LedgerEntryType.CREDIT, LedgerEntryType.DEBIT, OVERDRAFT_FEE), types(engine));

        LedgerCommand reversal = LedgerCommand.reversal("R1", 6, 2, "A", "D1");
        engine.process(reversal);
        engine.process(reversal);
        assertEquals(Money.of(AED, "100"), engine.balance("A", 2));
        assertEquals(Money.of(AED, "100"), engine.balance("A", 6));
        assertEquals(List.of(LedgerEntryType.CREDIT, LedgerEntryType.DEBIT, OVERDRAFT_FEE,
                LedgerEntryType.REVERSAL, OVERDRAFT_FEE_REVERSAL), types(engine));
        assertEquals("event:D1", engine.entries().get(3).referenceId());
        assertEquals("fee:A/D2/OVERDRAFT", engine.entries().get(4).referenceId());
        assertTrue(engine.entries().stream().noneMatch(entry -> entry.valueDay() == 3
                && entry.type() == OVERDRAFT_FEE));
    }

    @Test
    void invalidOrRepeatedReversalsDoNotMoveMoneyOrConsumeEventIds() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", Money.of(AED, "100")), new Account("B", Money.of(AED, "100"))));
        engine.process(new LedgerCommand("D1", 1, 1, DEBIT, "A", Money.of(AED, "10")));
        assertCode(UNKNOWN_EVENT, () -> engine.process(LedgerCommand.reversal("R1", 2, 1, "A", "missing")));
        assertCode(EVENT_ACCOUNT_MISMATCH,
                () -> engine.process(LedgerCommand.reversal("R1", 2, 1, "B", "D1")));
        engine.process(LedgerCommand.reversal("R1", 2, 1, "A", "D1"));
        assertCode(EVENT_ALREADY_REVERSED,
                () -> engine.process(LedgerCommand.reversal("R2", 3, 1, "A", "D1")));
        engine.process(new LedgerCommand("H1", 3, 3, AUTHORIZATION, "A", Money.of(AED, "1"), "Auth"));
        assertCode(EVENT_NOT_REVERSIBLE,
                () -> engine.process(LedgerCommand.reversal("R2", 3, 3, "A", "H1")));
        assertEquals(Money.of(AED, "100"), engine.balance("A", 6));
    }

    @Test
    void allocationPreservesBhdTotalAndReversalCompensatesEveryPart() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("B", Money.of(BHD, "0"))));
        LedgerCommand credit = LedgerCommand.allocatedCredit("E10", 5, 5, "B", Money.of(BHD, "10"), 3);
        engine.process(credit);
        assertEquals(List.of(Money.of(BHD, "3.334"), Money.of(BHD, "3.333"), Money.of(BHD, "3.333")),
                engine.entries().stream().map(entry -> entry.amount()).toList());
        assertEquals(Money.of(BHD, "10"), engine.balance("B", 5));
        engine.process(LedgerCommand.reversal("R10", 6, 5, "B", "E10"));
        assertEquals(6, engine.entries().size());
        assertEquals(Money.of(BHD, "0"), engine.balance("B", 5));
    }

    @Test
    void interestUsesPositiveClosingBalancesAndCapitalizesRoundedDailyTotalsOnce() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", Money.of(AED, "12.50")),
                new Account("B", Money.of(BHD, "10")),
                new Account("N", Money.of(AED, "-1"))));
        assertEquals(Money.of(AED, "0.00"), engine.dailyInterest("A", 1));
        assertEquals(Money.of(BHD, "0.004"), engine.dailyInterest("B", 1));
        assertEquals(Money.of(AED, "0"), engine.dailyInterest("N", 1));
        engine.capitalizeInterest(1, 6);
        engine.capitalizeInterest(1, 6);
        assertEquals(Money.of(BHD, "10.024"), engine.balance("B", 6));
        assertEquals(1, engine.entries().stream()
                .filter(entry -> entry.type() == INTEREST_CAPITALIZATION).count());
        assertEquals(Money.of(BHD, "0.024"), engine.entries().get(0).amount());
    }

    @Test
    void completeReplayProducesDeterministicBalancesErrorsFeesAndInterest() {
        ReplayReport report = LedgerApplication.run();
        ReplayReport replay = LedgerApplication.run();
        assertEquals(report, replay);
        assertEquals(12, report.days().size());
        assertEquals(UNKNOWN_AUTHORIZATION, report.errors().get(0).code());
        assertEquals("E6", report.errors().get(0).eventId());

        DailyReport aedDay2 = day(report, 2, "ACC-001");
        assertEquals(Money.of(AED, "250"), aedDay2.closingLedgerBalance());
        assertEquals(Money.of(AED, "50"), aedDay2.availableBalance());
        assertEquals(List.of(OVERDRAFT_FEE, OVERDRAFT_FEE_REVERSAL),
                aedDay2.fees().stream().map(entry -> entry.type()).toList());
        assertEquals(AuthorizationStatus.APPROVED, aedDay2.authorizationStates().get("Auth-A"));

        DailyReport aedDay6 = day(report, 6, "ACC-001");
        assertEquals(Money.of(AED, "466.03"), aedDay6.closingLedgerBalance());
        assertEquals(AuthorizationStatus.SETTLED, aedDay6.authorizationStates().get("Auth-A"));
        assertEquals(AuthorizationStatus.DECLINED, aedDay6.authorizationStates().get("Auth-B"));
        assertEquals(Money.of(AED, "0.19"), aedDay6.dailyInterest());

        DailyReport bhdDay6 = day(report, 6, "ACC-002");
        assertEquals(Money.of(BHD, "10.008"), bhdDay6.closingLedgerBalance());
        assertEquals(Money.of(BHD, "0.004"), bhdDay6.dailyInterest());
        assertTrue(report.days().stream().filter(value -> value.day() != 2)
                .allMatch(value -> value.fees().isEmpty()));
        assertTrue(report.render().contains("D4 ACC-001"));
        assertTrue(report.render().contains("errors=[UNKNOWN_AUTHORIZATION]"));
    }

    @Disabled("Conflicts with the nonnegative-available approval rule; AMBIGUITIES §2")
    @Test
    void alternativeCriterionWouldKeepAuthBActive() {
        assertEquals(AuthorizationStatus.APPROVED,
                day(LedgerApplication.run(), 6, "ACC-001").authorizationStates().get("Auth-B"));
    }

    private static List<LedgerEntryType> types(LedgerEngine engine) {
        return engine.entries().stream().map(entry -> entry.type()).toList();
    }

    private static DailyReport day(ReplayReport report, int day, String accountId) {
        return report.days().stream()
                .filter(value -> value.day() == day && value.accountId().equals(accountId))
                .findFirst().orElseThrow();
    }

    private static void assertCode(LedgerValidationException.Code code, Runnable action) {
        assertEquals(code, assertThrows(LedgerValidationException.class, action::run).code());
    }
}
