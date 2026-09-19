package ledger.service;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.exception.LedgerArgumentException;
import ledger.domain.exception.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.LedgerCommandPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ledger.domain.enums.AuthorizationStatus.APPROVED;
import static ledger.domain.enums.AuthorizationStatus.DECLINED;
import static ledger.domain.exception.LedgerValidationException.Code.CONFLICTING_EVENT_ID;
import static ledger.domain.exception.LedgerValidationException.Code.CURRENCY_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.DUPLICATE_AUTHORIZATION_ID;
import static ledger.domain.exception.LedgerValidationException.Code.INVALID_BUSINESS_DAY;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_ACCOUNT;
import static ledger.domain.enums.Currency.AED;
import static ledger.domain.enums.Currency.BHD;
import static ledger.domain.enums.CommandType.AUTHORIZATION;
import static ledger.domain.enums.CommandType.CREDIT;
import static ledger.domain.enums.CommandType.DEBIT;
import static ledger.domain.enums.LedgerEntryType.OVERDRAFT_FEE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationTest {
    private final List<Account> accounts = List.of(
            new Account("A", AED, Money.of(AED, "0")), new Account("B", BHD, Money.of(BHD, "10")));
    private final LedgerEngine engine = new LedgerEngine(accounts);

    @Test
    void e3ReservesFundsWithoutBookingMoneyAndDoesNotExpire() {
        engine.process(new LedgerCommandPayload("E1", 1, 1, CREDIT, "A", Money.of(AED, "1200")));
        engine.process(new LedgerCommandPayload("E2", 1, 1, DEBIT, "A", Money.of(AED, "950")));
        var entries = engine.entries();
        LedgerCommandPayload request = new LedgerCommandPayload("E3", 2, 2, AUTHORIZATION, "A", Money.of(AED, "200"), "Auth-A");
        engine.process(request);
        engine.process(request);
        assertEquals(List.of(new Authorization("Auth-A", "A", Money.of(AED, "200"), 2, APPROVED)),
                engine.authorizations());
        assertEquals(entries, engine.entries());
        assertEquals(Money.of(AED, "250"), engine.balance("A", 2));
        assertEquals(Money.of(AED, "50"), engine.availableBalance("A", 2));
        assertEquals(Money.of(AED, "50"), engine.availableBalance("A", 6));
        assertEquals(Money.of(AED, "50"), engine.availableBalance("A", 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "199.99"})
    void insufficientFundsCreateDeclinedRecordWithoutMovement(String opening) {
        LedgerEngine ledger = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, opening))));
        ledger.process(new LedgerCommandPayload("E3", 2, 2, AUTHORIZATION, "A", Money.of(AED, "200"), "Auth-A"));
        assertEquals(DECLINED, ledger.authorizations().get(0).status());
        assertEquals(Money.of(AED, opening), ledger.availableBalance("A", 2));
        assertEquals(Money.of(AED, opening), ledger.balance("A", 2));
        assertTrue(ledger.entries().isEmpty());
    }

    @Test
    void negativeOpeningBalanceAccruesFeesOnDaysReachedWithoutMovement() {
        LedgerEngine ledger = new LedgerEngine(List.of(new Account("A", AED, Money.of(AED, "-1"))));
        ledger.process(new LedgerCommandPayload("E3", 2, 2, AUTHORIZATION, "A", Money.of(AED, "200"), "Auth-A"));
        assertEquals(DECLINED, ledger.authorizations().get(0).status());
        assertEquals(List.of(1, 2), ledger.entries().stream()
                .filter(entry -> entry.type() == OVERDRAFT_FEE)
                .map(entry -> entry.valueDay()).toList());
        assertEquals(Money.of(AED, "-51"), ledger.balance("A", 2));
    }

    @Test
    void multipleHoldsUseExactBhdPrecisionAndRemainIsolatedByAccount() {
        engine.process(new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "3.334"), "One"));
        engine.process(new LedgerCommandPayload("H2", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "6.666"), "Two"));
        engine.process(new LedgerCommandPayload("H3", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "0.001"), "Three"));
        assertEquals(List.of(APPROVED, APPROVED, DECLINED),
                engine.authorizations().stream().map(Authorization::status).toList());
        assertEquals(Money.of(BHD, "0.000"), engine.availableBalance("B", 1));
        assertEquals(Money.of(BHD, "10.000"), engine.balance("B", 1));
        assertEquals(Money.of(AED, "0.00"), engine.availableBalance("A", 1));
        assertTrue(engine.entries().isEmpty());
    }

    @Test
    void declinedRetryKeepsOriginalDecisionAfterFunding() {
        LedgerCommandPayload request = new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "A", Money.of(AED, "10"), "One");
        engine.process(request);
        engine.process(new LedgerCommandPayload("C1", 2, 1, CREDIT, "A", Money.of(AED, "10")));
        engine.process(request);
        assertEquals(List.of(new Authorization("One", "A", Money.of(AED, "10"), 1, DECLINED)),
                engine.authorizations());
        assertEquals(Money.of(AED, "10"), engine.availableBalance("A", 2));
        engine.process(new LedgerCommandPayload("H2", 2, 2, AUTHORIZATION, "A", Money.of(AED, "10"), "Two"));
        assertEquals(APPROVED, engine.authorizations().get(1).status());
        assertEquals(Money.of(AED, "0"), engine.availableBalance("A", 2));
    }

    @Test
    void duplicateAuthorizationIdsAreGlobalAndDoNotConsumeEventIds() {
        engine.process(new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "A", Money.of(AED, "1"), "One"));
        var before = engine.authorizations();
        var error = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("H2", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "1"), "One")));
        assertEquals(DUPLICATE_AUTHORIZATION_ID, error.code());
        assertEquals(before, engine.authorizations());
        assertEquals(Money.of(BHD, "10"), engine.availableBalance("B", 1));
        engine.process(new LedgerCommandPayload("H2", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "1"), "Two"));
        var approvedDuplicate = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("H3", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "1"), "Two")));
        assertEquals(DUPLICATE_AUTHORIZATION_ID, approvedDuplicate.code());
        assertEquals(2, engine.authorizations().size());
        assertEquals(Money.of(BHD, "9"), engine.availableBalance("B", 1));
        assertTrue(engine.entries().isEmpty());
    }

    @Test
    void changingAuthorizationReferenceOnRetryIsAnEventConflict() {
        engine.process(new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "1"), "One"));
        var before = engine.authorizations();
        var error = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "1"), "Two")));
        assertEquals(CONFLICTING_EVENT_ID, error.code());
        assertEquals(before, engine.authorizations());
        assertEquals(Money.of(BHD, "9"), engine.availableBalance("B", 1));
    }

    @Test
    void invalidAccountOrCurrencyDoesNotConsumeAuthorizationOrEventIds() {
        var unknown = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "UNKNOWN", Money.of(BHD, "1"), "One")));
        var mismatch = assertThrows(LedgerValidationException.class, () -> engine.process(
                new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "B", Money.of(AED, "1"), "One")));
        assertEquals(UNKNOWN_ACCOUNT, unknown.code());
        assertEquals(CURRENCY_MISMATCH, mismatch.code());
        assertTrue(engine.authorizations().isEmpty());
        assertTrue(engine.entries().isEmpty());
        engine.process(new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "1"), "One"));
        assertEquals(APPROVED, engine.authorizations().get(0).status());
    }

    @Test
    void approvalUsesPostedDayAndAllKnownHoldsInCallerOrder() {
        engine.process(new LedgerCommandPayload("C1", 5, 5, CREDIT, "A", Money.of(AED, "10")));
        engine.process(new LedgerCommandPayload("H1", 2, 2, AUTHORIZATION, "A", Money.of(AED, "10"), "Early"));
        assertEquals(DECLINED, engine.authorizations().get(0).status());
        engine.process(new LedgerCommandPayload("H2", 6, 1, AUTHORIZATION, "A", Money.of(AED, "8"), "Later"));
        engine.process(new LedgerCommandPayload("H3", 5, 5, AUTHORIZATION, "A", Money.of(AED, "3"), "Earlier"));
        assertEquals(List.of(DECLINED, APPROVED, DECLINED),
                engine.authorizations().stream().map(Authorization::status).toList());
        assertEquals(Money.of(AED, "2"), engine.availableBalance("A", 5));
    }

    @Test
    void backValuedDebitChangesAvailableFundsWithoutRewritingApprovedHold() {
        engine.process(new LedgerCommandPayload("H1", 2, 2, AUTHORIZATION, "B", Money.of(BHD, "8"), "One"));
        var before = engine.authorizations();
        engine.process(new LedgerCommandPayload("D1", 5, 1, DEBIT, "B", Money.of(BHD, "5")));
        assertEquals(before, engine.authorizations());
        assertEquals(Money.of(BHD, "5"), engine.balance("B", 2));
        assertEquals(Money.of(BHD, "-3"), engine.availableBalance("B", 2));
    }

    @Test
    void authorizationSnapshotsAreImmutableAndReplayIsDeterministic() {
        List<LedgerCommandPayload> events = List.of(
                new LedgerCommandPayload("H1", 2, 2, AUTHORIZATION, "B", Money.of(BHD, "8"), "One"),
                new LedgerCommandPayload("H2", 1, 1, AUTHORIZATION, "B", Money.of(BHD, "3"), "Two"));
        engine.process(events.get(0));
        var snapshot = engine.authorizations();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        engine.process(events.get(1));
        assertEquals(1, snapshot.size());
        LedgerEngine replay = new LedgerEngine(accounts);
        events.forEach(replay::process);
        assertEquals(engine.authorizations(), replay.authorizations());
        assertEquals(engine.entries(), replay.entries());
        assertEquals(engine.availableBalance("B", 6), replay.availableBalance("B", 6));
    }

    @Test
    void validatesAuthorizationInputsAndAvailableBalanceQueries() {
        Money amount = Money.of(AED, "1");
        assertAll(
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "A", amount)),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "A", amount, " ")),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("C1", 1, 1, CREDIT, "A", amount, "One")),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION, "A", Money.of(AED, "0"), "One")),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new Authorization(" ", "A", amount, 1, APPROVED)),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new Authorization("One", "A", amount, 0, APPROVED)),
                () -> assertThrows(LedgerArgumentException.class,
                        () -> new Authorization("One", "A", Money.of(AED, "-1"), 1, APPROVED)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Authorization("One", "A", amount, 1, null)),
                () -> assertEquals(INVALID_BUSINESS_DAY,
                        assertThrows(LedgerValidationException.class,
                                () -> engine.availableBalance("A", 0)).code()),
                () -> assertEquals(UNKNOWN_ACCOUNT, assertThrows(LedgerValidationException.class,
                        () -> engine.availableBalance("UNKNOWN", 1)).code()));
    }
}
