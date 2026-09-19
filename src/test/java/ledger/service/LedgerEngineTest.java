package ledger.service;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.exception.LedgerArgumentException;
import ledger.domain.exception.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.enums.LedgerEntryType;
import ledger.service.command.dto.LedgerCommandPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ledger.domain.exception.LedgerValidationException.Code.CONFLICTING_EVENT_ID;
import static ledger.domain.exception.LedgerValidationException.Code.CURRENCY_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.INVALID_BUSINESS_DAY;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_ACCOUNT;
import static ledger.domain.enums.Currency.AED;
import static ledger.domain.enums.Currency.BHD;
import static ledger.service.command.dto.CommandType.CREDIT;
import static ledger.service.command.dto.CommandType.DEBIT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerEngineTest {
    private final Account account = new Account("ACC-001", AED, Money.of(AED, "0"));
    private final LedgerEngine engine = new LedgerEngine(List.of(account));

    @Test
    void creditCreatesPositiveEntryAndIncreasesBalance() {
        LedgerCommandPayload credit = new LedgerCommandPayload("E1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1200"));
        engine.process(credit);
        assertEquals(Money.of(AED, "1200"), engine.balance("ACC-001", 1));
        assertEquals(List.of(new LedgerEntry("event:E1", "E1", "ACC-001",
                Money.of(AED, "1200"), 1, LedgerEntryType.CREDIT)), engine.entries());
    }

    @Test
    void debitCreatesNegativeEntryAndDerivesE1E2Balance() {
        engine.process(new LedgerCommandPayload("E1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1200")));
        engine.process(new LedgerCommandPayload("E2", 1, 1, DEBIT, "ACC-001", Money.of(AED, "950")));
        assertEquals(Money.of(AED, "250"), engine.balance("ACC-001", 1));
        assertEquals(Money.of(AED, "-950"), engine.entries().get(1).amount());
        assertEquals(LedgerEntryType.DEBIT, engine.entries().get(1).type());
    }

    @Test
    void bookedDebitCanOverdrawAnAccount() {
        engine.process(new LedgerCommandPayload("D1", 1, 1, DEBIT, "ACC-001", Money.of(AED, "10")));
        assertEquals(Money.of(AED, "-35"), engine.balance("ACC-001", 1));
    }

    @Test
    void balanceIncludesOpeningAmountAndOnlyEntriesThroughRequestedValueDay() {
        LedgerEngine ledger = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, "50"))));
        assertEquals(Money.of(AED, "50"), ledger.balance("A", 1));
        ledger.process(new LedgerCommandPayload("C1", 1, 1, CREDIT, "A", Money.of(AED, "20")));
        ledger.process(new LedgerCommandPayload("D1", 2, 2, DEBIT, "A", Money.of(AED, "10")));
        assertAll(
                () -> assertEquals(Money.of(AED, "70"), ledger.balance("A", 1)),
                () -> assertEquals(Money.of(AED, "60"), ledger.balance("A", 2)),
                () -> assertEquals(Money.of(AED, "60"), ledger.balance("A", 6)));
    }

    @Test
    void balancesAreIsolatedByAccountAndCurrency() {
        LedgerEngine ledger = new LedgerEngine(List.of(account, new Account("ACC-002", BHD, Money.of(BHD, "0"))));
        ledger.process(new LedgerCommandPayload("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        ledger.process(new LedgerCommandPayload("C2", 1, 1, CREDIT, "ACC-002", Money.of(BHD, "3.334")));
        assertEquals(Money.of(AED, "10"), ledger.balance("ACC-001", 1));
        assertEquals(Money.of(BHD, "3.334"), ledger.balance("ACC-002", 1));
    }

    @Test
    void entrySnapshotsCannotMutateOrObserveLaterAppends() {
        engine.process(new LedgerCommandPayload("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        List<LedgerEntry> snapshot = engine.entries();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        engine.process(new LedgerCommandPayload("D1", 1, 1, DEBIT, "ACC-001", Money.of(AED, "2")));
        assertEquals(1, snapshot.size());
        assertEquals(snapshot.get(0), engine.entries().get(0));
        assertEquals(2, engine.entries().size());
    }

    @Test
    void identicalEventRetryDoesNotMoveMoneyTwice() {
        LedgerCommandPayload event = new LedgerCommandPayload("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10"));
        engine.process(event);
        engine.process(new LedgerCommandPayload("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10.00")));
        assertEquals(1, engine.entries().size());
        assertEquals(Money.of(AED, "10"), engine.balance("ACC-001", 1));
    }

    @Test
    void conflictingEventIdIsRejectedWithoutMovement() {
        engine.process(new LedgerCommandPayload("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        LedgerValidationException error = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("C1", 1, 1, DEBIT, "ACC-001", Money.of(AED, "10"))));
        assertEquals(CONFLICTING_EVENT_ID, error.code());
        assertEquals(1, engine.entries().size());
        assertEquals(Money.of(AED, "10"), engine.balance("ACC-001", 1));
    }

    @Test
    void rejectedEventsLeaveNoEntriesOrConsumedIds() {
        LedgerValidationException unknownAccount = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("C1", 1, 1, CREDIT, "UNKNOWN", Money.of(AED, "10"))));
        assertEquals(UNKNOWN_ACCOUNT, unknownAccount.code());
        LedgerValidationException currencyMismatch = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("C1", 1, 1, CREDIT, "ACC-001", Money.of(BHD, "10"))));
        assertEquals(CURRENCY_MISMATCH, currencyMismatch.code());
        assertTrue(engine.entries().isEmpty());
        engine.process(new LedgerCommandPayload("C1", 1, 1, CREDIT, "ACC-001", Money.of(AED, "10")));
        assertEquals(Money.of(AED, "10"), engine.balance("ACC-001", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveInputTransfers(String amount) {
        assertAll(
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("C", 1, 1, CREDIT, "ACC-001", Money.of(AED, amount))),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("D", 1, 1, DEBIT, "ACC-001", Money.of(AED, amount))));
    }

    @Test
    void validatesIdentifiersDaysAndEntrySigns() {
        assertAll(
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new Account(" ", AED, Money.of(AED, "0"))),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new Account("A", BHD, Money.of(AED, "0"))),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("", 1, 1, CREDIT, "ACC-001", Money.of(AED, "1"))),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("C", 0, 1, CREDIT, "ACC-001", Money.of(AED, "1"))),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("C", 1, 0, CREDIT, "ACC-001", Money.of(AED, "1"))),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerEntry("D", "D", "ACC-001", Money.of(AED, "1"), 1, LedgerEntryType.DEBIT)),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerEntry("C", "C", "ACC-001", Money.of(AED, "-1"), 1, LedgerEntryType.CREDIT)),
                () -> assertEquals(INVALID_BUSINESS_DAY,
                        assertThrows(LedgerValidationException.class,
                                () -> engine.balance("ACC-001", 0)).code()));
    }

    @Test
    void balanceRejectsUnknownAccountWithErrorCode() {
        LedgerValidationException error = assertThrows(LedgerValidationException.class,
                () -> engine.balance("UNKNOWN", 1));
        assertEquals(UNKNOWN_ACCOUNT, error.code());
    }

    @Test
    void rejectsDuplicateAccountRegistration() {
        assertThrows(LedgerArgumentException.class, () -> new LedgerEngine(List.of(account, account)));
    }
}
