package ledger.service;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.Authorization;
import ledger.domain.exception.LedgerArgumentException;
import ledger.domain.exception.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.enums.LedgerEntryType;
import ledger.domain.LedgerCommandPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ledger.domain.enums.AuthorizationStatus.APPROVED;
import static ledger.domain.enums.AuthorizationStatus.DECLINED;
import static ledger.domain.enums.AuthorizationStatus.SETTLED;
import static ledger.domain.exception.LedgerValidationException.Code.AUTHORIZATION_ACCOUNT_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.AUTHORIZATION_NOT_APPROVED;
import static ledger.domain.exception.LedgerValidationException.Code.CONFLICTING_EVENT_ID;
import static ledger.domain.exception.LedgerValidationException.Code.CURRENCY_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.DUPLICATE_AUTHORIZATION_ID;
import static ledger.domain.exception.LedgerValidationException.Code.SETTLEMENT_EXCEEDS_AUTHORIZATION;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_ACCOUNT;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_AUTHORIZATION;
import static ledger.domain.enums.Currency.AED;
import static ledger.domain.enums.Currency.BHD;
import static ledger.domain.enums.CommandType.AUTHORIZATION;
import static ledger.domain.enums.CommandType.CREDIT;
import static ledger.domain.enums.CommandType.DEBIT;
import static ledger.domain.enums.CommandType.SETTLEMENT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementTest {
    private final List<Account> accounts = List.of(
            new Account("A", AED, Money.of(AED, "250")),
            new Account("B", AED, Money.of(AED, "250")),
            new Account("C", BHD, Money.of(BHD, "10")));
    private final LedgerEngine engine = new LedgerEngine(accounts);
    private final LedgerCommandPayload hold = new LedgerCommandPayload("E3", 2, 2, AUTHORIZATION,
            "A", Money.of(AED, "200"), "Auth-A");
    private final LedgerCommandPayload settlement = new LedgerCommandPayload("E5", 4, 4, SETTLEMENT,
            "A", Money.of(AED, "185"), "Auth-A");

    @Test
    void smallerSettlementReleasesEntireHoldAndPreservesSnapshots() {
        engine.process(hold);
        engine.process(new LedgerCommandPayload("E4", 3, 3, CREDIT, "A", Money.of(AED, "400")));
        var oldEntries = engine.entries();
        var oldAuthorizations = engine.authorizations();
        assertEquals(Money.of(AED, "450"), engine.availableBalance("A", 4));
        engine.process(settlement);
        assertEquals(List.of(new Authorization("Auth-A", "A", Money.of(AED, "200"), 2, SETTLED, 4)),
                engine.authorizations());
        assertEquals(new LedgerEntry("event:E5", "E5", "A", Money.of(AED, "-185"),
                4, LedgerEntryType.SETTLEMENT, "Auth-A"), engine.entries().get(1));
        assertEquals(Money.of(AED, "465"), engine.balance("A", 4));
        assertEquals(Money.of(AED, "465"), engine.availableBalance("A", 4));
        assertEquals(Money.of(AED, "650"), engine.balance("A", 3));
        assertEquals(1, oldEntries.size());
        assertEquals(oldEntries.get(0), engine.entries().get(0));
        assertEquals(APPROVED, oldAuthorizations.get(0).status());
        assertEquals(Money.of(AED, "250"), engine.availableBalance("B", 4));
    }

    @Test
    void unknownAuthorizationDoesNotCreateDebitOrConsumeEventId() {
        engine.process(hold);
        assertRejected(UNKNOWN_AUTHORIZATION, new LedgerCommandPayload("E6", 4, 4, SETTLEMENT,
                "A", Money.of(AED, "180"), "Auth-Z"));
        assertTrue(engine.entries().isEmpty());
        engine.process(new LedgerCommandPayload("E6", 4, 4, SETTLEMENT,
                "A", Money.of(AED, "180"), "Auth-A"));
        assertEquals(Money.of(AED, "70"), engine.balance("A", 4));
    }

    @Test
    void settlementRequiresMatchingAccountAndCurrencyWithoutConsumingEventId() {
        engine.process(hold);
        assertRejected(AUTHORIZATION_ACCOUNT_MISMATCH, new LedgerCommandPayload("E5", 4, 4, SETTLEMENT,
                "B", Money.of(AED, "185"), "Auth-A"));
        assertRejected(AUTHORIZATION_ACCOUNT_MISMATCH, new LedgerCommandPayload("E5", 4, 4, SETTLEMENT,
                "C", Money.of(BHD, "1"), "Auth-A"));
        assertRejected(CURRENCY_MISMATCH, new LedgerCommandPayload("E5", 4, 4, SETTLEMENT,
                "A", Money.of(BHD, "185"), "Auth-A"));
        assertRejected(UNKNOWN_ACCOUNT, new LedgerCommandPayload("E5", 4, 4, SETTLEMENT,
                "UNKNOWN", Money.of(AED, "185"), "Auth-A"));
        engine.process(settlement);
        assertEquals(SETTLED, engine.authorizations().get(0).status());
    }

    @Test
    void declinedAuthorizationCannotSettleEvenAfterFunding() {
        engine.process(new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION,
                "A", Money.of(AED, "300"), "Declined"));
        engine.process(new LedgerCommandPayload("C1", 2, 2, CREDIT, "A", Money.of(AED, "100")));
        assertRejected(AUTHORIZATION_NOT_APPROVED, new LedgerCommandPayload("S1", 3, 3, SETTLEMENT,
                "A", Money.of(AED, "100"), "Declined"));
        assertEquals(DECLINED, engine.authorizations().get(0).status());
    }

    @Test
    void overCaptureLeavesHoldIntactAndCorrectedRetryCanSettle() {
        engine.process(hold);
        assertRejected(SETTLEMENT_EXCEEDS_AUTHORIZATION, new LedgerCommandPayload("E5", 4, 4, SETTLEMENT,
                "A", Money.of(AED, "200.01"), "Auth-A"));
        engine.process(settlement);
        assertEquals(Money.of(AED, "65"), engine.availableBalance("A", 4));
    }

    @Test
    void settlementRetriesAreIdempotentAndSecondCaptureIsRejected() {
        engine.process(hold);
        engine.process(settlement);
        engine.process(settlement);
        engine.process(hold);
        assertEquals(1, engine.entries().size());
        assertEquals(SETTLED, engine.authorizations().get(0).status());
        assertEquals(Money.of(AED, "65"), engine.availableBalance("A", 4));
        assertRejected(AUTHORIZATION_NOT_APPROVED, new LedgerCommandPayload("S2", 5, 5, SETTLEMENT,
                "A", Money.of(AED, "15"), "Auth-A"));
        assertRejected(CONFLICTING_EVENT_ID, new LedgerCommandPayload("E5", 4, 4, SETTLEMENT,
                "A", Money.of(AED, "184"), "Auth-A"));
        assertRejected(DUPLICATE_AUTHORIZATION_ID, new LedgerCommandPayload("H2", 5, 5, AUTHORIZATION,
                "A", Money.of(AED, "1"), "Auth-A"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.334", "0.001"})
    void exactAndSmallerBhdSettlementsReleaseOnlyReferencedHold(String amount) {
        engine.process(new LedgerCommandPayload("H1", 1, 1, AUTHORIZATION,
                "C", Money.of(BHD, "3.334"), "One"));
        engine.process(new LedgerCommandPayload("H2", 1, 1, AUTHORIZATION,
                "C", Money.of(BHD, "2"), "Two"));
        assertRejected(SETTLEMENT_EXCEEDS_AUTHORIZATION, new LedgerCommandPayload("S1", 2, 2, SETTLEMENT,
                "C", Money.of(BHD, "3.335"), "One"));
        engine.process(new LedgerCommandPayload("S1", 2, 2, SETTLEMENT, "C", Money.of(BHD, amount), "One"));
        assertEquals(List.of(SETTLED, APPROVED),
                engine.authorizations().stream().map(Authorization::status).toList());
        assertEquals(Money.of(BHD, "10").add(Money.of(BHD, amount).negate()), engine.balance("C", 2));
        assertEquals(Money.of(BHD, "8").add(Money.of(BHD, amount).negate()), engine.availableBalance("C", 2));
    }

    @Test
    void backValuedSettlementHonorsApprovedHoldDespiteLaterInsufficientFundsAndReplaysDeterministically() {
        List<LedgerCommandPayload> events = List.of(hold,
                new LedgerCommandPayload("D1", 6, 1, DEBIT, "A", Money.of(AED, "100")),
                new LedgerCommandPayload("S1", 5, 2, SETTLEMENT, "A", Money.of(AED, "200"), "Auth-A"));
        events.forEach(engine::process);
        assertEquals(Money.of(AED, "150"), engine.balance("A", 1));
        assertEquals(Money.of(AED, "-75"), engine.balance("A", 2));
        assertEquals(Money.of(AED, "-75"), engine.availableBalance("A", 2));
        assertEquals(SETTLED, engine.authorizations().get(0).status());
        LedgerEngine replay = new LedgerEngine(accounts);
        events.forEach(replay::process);
        assertEquals(engine.entries(), replay.entries());
        assertEquals(engine.authorizations(), replay.authorizations());
        assertEquals(engine.availableBalance("A", 6), replay.availableBalance("A", 6));
    }

    @Test
    void settlementRequiresAuthorizationReferenceAndDebitEntrySign() {
        assertAll(
                () -> assertThrows(LedgerArgumentException.class, () -> new LedgerCommandPayload(
                        "S1", 1, 1, SETTLEMENT, "A", Money.of(AED, "1"))),
                () -> assertThrows(LedgerArgumentException.class, () -> new LedgerCommandPayload(
                        "S1", 1, 1, SETTLEMENT, "A", Money.of(AED, "1"), " ")),
                () -> assertThrows(LedgerArgumentException.class, () -> new LedgerEntry(
                        "S1", "S1", "A", Money.of(AED, "-1"), 1, LedgerEntryType.SETTLEMENT)),
                () -> assertThrows(LedgerArgumentException.class, () -> new LedgerEntry(
                        "S1", "S1", "A", Money.of(AED, "-1"), 1, LedgerEntryType.SETTLEMENT, " ")),
                () -> assertThrows(LedgerArgumentException.class, () -> new LedgerEntry(
                        "S1", "S1", "A", Money.of(AED, "1"), 1, LedgerEntryType.SETTLEMENT, "Auth-A")),
                () -> assertThrows(LedgerArgumentException.class, () -> new LedgerEntry(
                        "D1", "D1", "A", Money.of(AED, "-1"), 1, LedgerEntryType.DEBIT, "Auth-A")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void settlementRequiresPositiveInputAmount(String amount) {
        assertThrows(LedgerArgumentException.class, () -> new LedgerCommandPayload(
                "S1", 1, 1, SETTLEMENT, "A", Money.of(AED, amount), "Auth-A"));
    }

    private void assertRejected(LedgerValidationException.Code code, LedgerCommandPayload event) {
        var entries = engine.entries();
        var authorizations = engine.authorizations();
        var available = accounts.stream().map(account -> engine.availableBalance(account.accountId(), 6)).toList();
        var error = assertThrows(LedgerValidationException.class, () -> engine.process(event));
        assertEquals(code, error.code());
        assertEquals(entries, engine.entries());
        assertEquals(authorizations, engine.authorizations());
        assertEquals(available, accounts.stream().map(account -> engine.availableBalance(account.accountId(), 6)).toList());
    }
}
