package ledger.service;

import java.util.List;
import ledger.LedgerApplication;
import ledger.domain.Account;
import ledger.domain.enums.AuthorizationStatus;
import ledger.domain.exception.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.enums.LedgerEntryType;
import ledger.service.command.dto.LedgerCommandPayload;
import ledger.report.DailyReport;
import ledger.report.ReplayReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static ledger.domain.exception.LedgerValidationException.Code.EVENT_ACCOUNT_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.EVENT_ALREADY_REVERSED;
import static ledger.domain.exception.LedgerValidationException.Code.EVENT_NOT_REVERSIBLE;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_AUTHORIZATION;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_EVENT;
import static ledger.domain.enums.Currency.AED;
import static ledger.domain.enums.Currency.BHD;
import static ledger.service.command.dto.CommandType.AUTHORIZATION;
import static ledger.service.command.dto.CommandType.CREDIT;
import static ledger.service.command.dto.CommandType.DEBIT;
import static ledger.domain.enums.LedgerEntryType.INTEREST_CAPITALIZATION;
import static ledger.domain.enums.LedgerEntryType.OVERDRAFT_FEE;
import static ledger.domain.enums.LedgerEntryType.OVERDRAFT_FEE_REVERSAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerCompletionTest {
    @Test
    void backValuedDebitFeesEveryNegativeClosingDayAndReversalReversesEach() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, "0"))));
        engine.process(new LedgerCommandPayload("C1", 1, 1, CREDIT, "A", Money.of(AED, "100")));
        engine.process(new LedgerCommandPayload("D1", 5, 2, DEBIT, "A", Money.of(AED, "150")));
        assertEquals(Money.of(AED, "-75"), engine.balance("A", 2));
        assertEquals(Money.of(AED, "-150"), engine.balance("A", 5));
        assertEquals(List.of(LedgerEntryType.CREDIT, LedgerEntryType.DEBIT, OVERDRAFT_FEE,
                OVERDRAFT_FEE, OVERDRAFT_FEE, OVERDRAFT_FEE), types(engine));
        assertEquals(List.of(2, 3, 4, 5), engine.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());

        LedgerCommandPayload reversal = LedgerCommandPayload.reversal("R1", 6, 2, "A", "D1");
        engine.process(reversal);
        engine.process(reversal);
        assertEquals(Money.of(AED, "100"), engine.balance("A", 2));
        assertEquals(Money.of(AED, "100"), engine.balance("A", 6));
        assertEquals(List.of(LedgerEntryType.CREDIT, LedgerEntryType.DEBIT,
                OVERDRAFT_FEE, OVERDRAFT_FEE, OVERDRAFT_FEE, OVERDRAFT_FEE,
                LedgerEntryType.REVERSAL,
                OVERDRAFT_FEE_REVERSAL, OVERDRAFT_FEE_REVERSAL, OVERDRAFT_FEE_REVERSAL,
                OVERDRAFT_FEE_REVERSAL), types(engine));
        assertEquals("event:D1", engine.entries().get(6).referenceId());
        assertEquals("fee:A/D2/OVERDRAFT", engine.entries().get(7).referenceId());
        assertEquals(List.of(2, 3, 4, 5), engine.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE_REVERSAL)
                .map(entry -> entry.valueDay()).toList());
    }

    @Test
    void invalidOrRepeatedReversalsDoNotMoveMoneyOrConsumeEventIds() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", AED, Money.of(AED, "100")), new Account("B", AED, Money.of(AED, "100"))));
        engine.process(new LedgerCommandPayload("D1", 1, 1, DEBIT, "A", Money.of(AED, "10")));
        assertCode(UNKNOWN_EVENT, () -> engine.process(LedgerCommandPayload.reversal("R1", 2, 1, "A", "missing")));
        assertCode(EVENT_ACCOUNT_MISMATCH,
                () -> engine.process(LedgerCommandPayload.reversal("R1", 2, 1, "B", "D1")));
        engine.process(LedgerCommandPayload.reversal("R1", 2, 1, "A", "D1"));
        assertCode(EVENT_ALREADY_REVERSED,
                () -> engine.process(LedgerCommandPayload.reversal("R2", 3, 1, "A", "D1")));
        engine.process(new LedgerCommandPayload("H1", 3, 3, AUTHORIZATION, "A", Money.of(AED, "1"), "Auth"));
        assertCode(EVENT_NOT_REVERSIBLE,
                () -> engine.process(LedgerCommandPayload.reversal("R2", 3, 3, "A", "H1")));
        assertEquals(Money.of(AED, "100"), engine.balance("A", 6));
    }

    @Test
    void allocationPreservesBhdTotalAndReversalCompensatesEveryPart() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("B", BHD, Money.of(BHD, "0"))));
        LedgerCommandPayload credit = LedgerCommandPayload.allocatedCredit("E10", 5, 5, "B", Money.of(BHD, "10"), 3);
        engine.process(credit);
        assertEquals(List.of(Money.of(BHD, "3.334"), Money.of(BHD, "3.333"), Money.of(BHD, "3.333")),
                engine.entries().stream().map(entry -> entry.amount()).toList());
        assertEquals(Money.of(BHD, "10"), engine.balance("B", 5));
        engine.process(LedgerCommandPayload.reversal("R10", 6, 5, "B", "E10"));
        assertEquals(6, engine.entries().size());
        assertEquals(Money.of(BHD, "0"), engine.balance("B", 5));
    }

    @Test
    void reconciliationCoversDaysBeyondALaterArrivingCommandsPostedDay() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, "0"))));
        engine.process(new LedgerCommandPayload("D6", 6, 6, DEBIT, "A", Money.of(AED, "10")));
        assertEquals(List.of(6), engine.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());

        engine.process(new LedgerCommandPayload("C5", 5, 5, CREDIT, "A", Money.of(AED, "100")));
        assertEquals(Money.of(AED, "90"), engine.balance("A", 6));
        assertEquals(List.of(LedgerEntryType.DEBIT, OVERDRAFT_FEE,
                LedgerEntryType.CREDIT, OVERDRAFT_FEE_REVERSAL), types(engine));
        assertEquals(6, engine.entries().get(3).valueDay());
    }

    @Test
    void latestProcessedDayReconciliationFeesADayNegativeMadeByALaterPostedCommand() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, "50"))));
        engine.process(new LedgerCommandPayload("C6", 6, 6, CREDIT, "A", Money.of(AED, "20")));
        assertTrue(engine.entries().stream().noneMatch(entry -> entry.type() == OVERDRAFT_FEE));

        engine.process(new LedgerCommandPayload("D1", 5, 1, DEBIT, "A", Money.of(AED, "100")));
        assertEquals(Money.of(AED, "-180"), engine.balance("A", 6));
        assertEquals(List.of(1, 2, 3, 4, 5, 6), engine.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());
    }

    @Test
    void rejectedCommandsDoNotExtendLatestProcessedDay() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, "0"))));
        assertThrows(LedgerValidationException.class,
                () -> engine.process(new LedgerCommandPayload("X1", 100, 100, DEBIT, "A",
                        Money.of(BHD, "10"))));

        engine.process(new LedgerCommandPayload("D1", 1, 1, DEBIT, "A", Money.of(AED, "10")));
        assertEquals(List.of(1), engine.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());
        assertEquals(Money.of(AED, "-35"), engine.balance("A", 1));
    }

    @Test
    void authorizationAdvancingLatestProcessedDayAssessesItsNewlyReachedDay() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, "0"))));
        engine.process(new LedgerCommandPayload("D1", 1, 1, DEBIT, "A", Money.of(AED, "10")));
        assertEquals(List.of(1), engine.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());

        engine.process(new LedgerCommandPayload("H1", 2, 2, AUTHORIZATION, "A",
                Money.of(AED, "1"), "Auth"));
        assertEquals(List.of(1, 2), engine.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());
        assertEquals(Money.of(AED, "-60"), engine.balance("A", 2));
    }

    @Test
    void replayAssessesFeesThroughTheFinalReportedDay() {
        LedgerReplay replay = new LedgerReplay(List.of(new Account("A", AED, Money.of(AED, "0"))));
        List<LedgerCommandPayload> commands = List.of(
                new LedgerCommandPayload("C1", 1, 1, CREDIT, "A", Money.of(AED, "100")),
                new LedgerCommandPayload("D1", 4, 1, DEBIT, "A", Money.of(AED, "150")));
        ReplayReport report = replay.replay(commands, 1, 6);
        assertEquals(List.of(1, 2, 3, 4, 5, 6), report.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());
    }

    @Test
    void interestUsesPositiveClosingBalancesAndCapitalizesRoundedDailyTotalsOnce() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", AED, Money.of(AED, "12.50")),
                new Account("B", BHD, Money.of(BHD, "10")),
                new Account("N", AED, Money.of(AED, "-1"))));
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
        assertTrue(report.days().stream().filter(value -> value.day() != 2 && value.day() != 4
                && value.day() != 5).allMatch(value -> value.fees().isEmpty()));
        assertEquals(List.of(OVERDRAFT_FEE, OVERDRAFT_FEE_REVERSAL),
                day(report, 4, "ACC-001").fees().stream().map(entry -> entry.type()).toList());
        assertEquals(List.of(OVERDRAFT_FEE, OVERDRAFT_FEE_REVERSAL),
                day(report, 5, "ACC-001").fees().stream().map(entry -> entry.type()).toList());
        assertTrue(report.render().contains("D4 ACC-001"));
        assertTrue(report.render().contains("errors=[UNKNOWN_AUTHORIZATION]"));
    }

    /**
     * Intentionally failing test, required by the assessment and kept red.
     *
     * <p>It asserts acceptance criterion 7 — that each of E10's three BHD
     * instalments is 3.334 — against this implementation, which allocates
     * 3.334, 3.333, 3.333.
     *
     * <p>What the failure reveals: criterion 7 and the BHD 10.000 source
     * amount cannot both be satisfied, because 3.334 x 3 = 10.002. Honouring
     * the criterion would mint BHD 0.002 out of nothing on every three-part
     * allocation, breaking conservation — the one property a ledger cannot
     * trade away. The design refuses the criterion and keeps the total exact,
     * so this test must stay failing. Making it pass means the allocator was
     * changed to create money; see REJECTED.md criterion 7 and AMBIGUITIES §5.
     */
    @DisplayName("Intentional failure: criterion 7 creates BHD 0.002")
    @Test
    void criterionSevenWouldAllocateEveryInstalmentAsBhd3334() {
        LedgerEngine engine = new LedgerEngine(List.of(new Account("B", BHD, Money.of(BHD, "0"))));
        engine.process(LedgerCommandPayload.allocatedCredit("E10", 5, 5,
                "B", Money.of(BHD, "10"), 3));
        assertEquals(List.of(Money.of(BHD, "3.334"), Money.of(BHD, "3.334"),
                        Money.of(BHD, "3.334")),
                engine.entries().stream().map(entry -> entry.amount()).toList());
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
