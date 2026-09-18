package ledger.service;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.LedgerEntry;
import ledger.domain.LedgerEntryType;
import ledger.domain.LedgerEvent;
import ledger.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static ledger.domain.EventType.CREDIT;
import static ledger.domain.EventType.DEBIT;
import static org.junit.jupiter.api.Assertions.*;

class LedgerEngineTest {
    private final Account account = new Account("ACC-001", Money.of(AED, "0"));
    private final LedgerEngine engine = new LedgerEngine(List.of(account));

    @Test
    void creditCreatesPositiveEntryAndIncreasesBalance() {
        LedgerEvent credit = new LedgerEvent("E1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1200"));
        engine.process(credit);
        assertEquals(Money.of(AED, "1200"), engine.balance("ACC-001", 1));
        assertEquals(List.of(new LedgerEntry("event:E1", "E1", "ACC-001",
                Money.of(AED, "1200"), 1, LedgerEntryType.CREDIT)), engine.entries());
    }

    @Test
    void debitCreatesNegativeEntryAndDerivesE1E2Balance() {
        engine.process(new LedgerEvent("E1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1200")));
        engine.process(new LedgerEvent("E2", 1, 1, DEBIT, "ACC-001", Money.of(AED, "950")));
        assertEquals(Money.of(AED, "250"), engine.balance("ACC-001", 1));
        assertEquals(Money.of(AED, "-950"), engine.entries().get(1).amount());
        assertEquals(LedgerEntryType.DEBIT, engine.entries().get(1).type());
    }

    @Test
    void bookedDebitCanOverdrawAnAccount() {
        engine.process(new LedgerEvent("D1", 1, 1, DEBIT, "ACC-001", Money.of(AED, "10")));
        assertEquals(Money.of(AED, "-10"), engine.balance("ACC-001", 1));
    }

    @Test
    void balanceIncludesOpeningAmountAndOnlyEntriesThroughRequestedValueDay() {
        LedgerEngine ledger = new LedgerEngine(List.of(new Account("A", Money.of(AED, "50"))));
        assertEquals(Money.of(AED, "50"), ledger.balance("A", 1));
        ledger.process(new LedgerEvent("C1", 1, 1, CREDIT, "A", Money.of(AED, "20")));
        ledger.process(new LedgerEvent("D1", 2, 2, DEBIT, "A", Money.of(AED, "10")));
        assertAll(
                () -> assertEquals(Money.of(AED, "70"), ledger.balance("A", 1)),
                () -> assertEquals(Money.of(AED, "60"), ledger.balance("A", 2)),
                () -> assertEquals(Money.of(AED, "60"), ledger.balance("A", 6)));
    }

    @Test
    void balancesAreIsolatedByAccountAndCurrency() {
        LedgerEngine ledger = new LedgerEngine(List.of(account, new Account("ACC-002", Money.of(BHD, "0"))));
        ledger.process(new LedgerEvent("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        ledger.process(new LedgerEvent("C2", 1, 1, CREDIT, "ACC-002", Money.of(BHD, "3.334")));
        assertEquals(Money.of(AED, "10"), ledger.balance("ACC-001", 1));
        assertEquals(Money.of(BHD, "3.334"), ledger.balance("ACC-002", 1));
    }

    @Test
    void entrySnapshotsCannotMutateOrObserveLaterAppends() {
        engine.process(new LedgerEvent("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        List<LedgerEntry> snapshot = engine.entries();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        engine.process(new LedgerEvent("D1", 1, 1, DEBIT, "ACC-001", Money.of(AED, "2")));
        assertEquals(1, snapshot.size());
        assertEquals(snapshot.get(0), engine.entries().get(0));
        assertEquals(2, engine.entries().size());
    }

    @Test
    void identicalEventRetryDoesNotMoveMoneyTwice() {
        LedgerEvent event = new LedgerEvent("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10"));
        engine.process(event);
        engine.process(new LedgerEvent("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10.00")));
        assertEquals(1, engine.entries().size());
        assertEquals(Money.of(AED, "10"), engine.balance("ACC-001", 1));
    }

    @Test
    void conflictingEventIdIsRejectedWithoutMovement() {
        engine.process(new LedgerEvent("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        assertThrows(IllegalArgumentException.class, () -> engine.process(
                new LedgerEvent("C1", 1, 1, DEBIT, "ACC-001", Money.of(AED, "10"))));
        assertEquals(1, engine.entries().size());
        assertEquals(Money.of(AED, "10"), engine.balance("ACC-001", 1));
    }

    @Test
    void rejectedEventsLeaveNoEntriesOrConsumedIds() {
        assertThrows(IllegalArgumentException.class, () -> engine.process(
                new LedgerEvent("C1", 1, 1, CREDIT, "UNKNOWN", Money.of(AED, "10"))));
        assertThrows(IllegalArgumentException.class, () -> engine.process(
                new LedgerEvent("C1", 1, 1, CREDIT, "ACC-001", Money.of(BHD, "10"))));
        assertTrue(engine.entries().isEmpty());
        engine.process(new LedgerEvent("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        assertEquals(Money.of(AED, "10"), engine.balance("ACC-001", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveInputTransfers(String amount) {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LedgerEvent("C", 1, 1, CREDIT, "ACC-001", Money.of(AED, amount))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LedgerEvent("D", 1, 1, DEBIT, "ACC-001", Money.of(AED, amount))));
    }

    @Test
    void validatesIdentifiersDaysAndEntrySigns() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Account(" ", Money.of(AED, "0"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LedgerEvent("", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LedgerEvent("C", 0, 1, CREDIT, "ACC-001", Money.of(AED, "1"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LedgerEvent("C", 1, 0, CREDIT, "ACC-001", Money.of(AED, "1"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LedgerEntry("D", "D", "ACC-001", Money.of(AED, "1"), 1, LedgerEntryType.DEBIT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LedgerEntry("C", "C", "ACC-001", Money.of(AED, "-1"), 1, LedgerEntryType.CREDIT)),
                () -> assertThrows(IllegalArgumentException.class, () -> engine.balance("ACC-001", 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> engine.balance("UNKNOWN", 1)));
    }

    @Test
    void rejectsDuplicateAccountRegistration() {
        assertThrows(IllegalArgumentException.class, () -> new LedgerEngine(List.of(account, account)));
    }
}
