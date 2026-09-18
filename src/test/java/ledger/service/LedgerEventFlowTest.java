package ledger.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import ledger.domain.Account;
import ledger.domain.AuthorizationStatus;
import ledger.domain.LedgerValidationException;
import ledger.domain.LedgerDomainEvent;
import ledger.domain.Money;
import ledger.domain.LedgerCommand;
import org.junit.jupiter.api.Test;

import static ledger.domain.LedgerValidationException.Code.CONFLICTING_EVENT_ID;
import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static ledger.domain.CommandType.AUTHORIZATION;
import static ledger.domain.CommandType.CREDIT;
import static ledger.domain.CommandType.DEBIT;
import static ledger.domain.CommandType.SETTLEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerEventFlowTest {
    @Test
    void publishesAuthorizationAndSettlementAfterTheirStateChanges() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", Money.of(AED, "250"))));
        List<String> received = new ArrayList<>();
        engine.on(LedgerDomainEvent.AuthorizationProcessed.class, event -> {
            assertEquals(AuthorizationStatus.APPROVED,
                    engine.authorizations().get(0).status());
            received.add("authorization");
        });
        engine.on(LedgerDomainEvent.SettlementProcessed.class, event -> {
            assertEquals(AuthorizationStatus.SETTLED,
                    engine.authorizations().get(0).status());
            assertEquals(event.entries().get(0), engine.entries().get(0));
            received.add("settlement");
        });

        engine.process(new LedgerCommand("H1", 1, 1, AUTHORIZATION,
                "A", Money.of(AED, "200"), "Auth-A"));
        engine.process(new LedgerCommand("S1", 2, 2, SETTLEMENT,
                "A", Money.of(AED, "185"), "Auth-A"));

        assertEquals(List.of("authorization", "settlement"), received);
    }

    @Test
    void feeSubscriberPublishesItsResultAfterTheMovementEvent() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", Money.of(AED, "0"))));
        List<String> received = new ArrayList<>();
        engine.on(LedgerDomainEvent.MovementProcessed.class,
                event -> received.add("movement"));
        engine.on(LedgerDomainEvent.OverdraftFeeProcessed.class,
                event -> received.add("fee"));

        engine.process(new LedgerCommand("D1", 1, 1, DEBIT,
                "A", Money.of(AED, "10")));

        assertEquals(List.of("movement", "fee"), received);
        assertEquals(Money.of(AED, "-35"), engine.balance("A", 1));
    }

    @Test
    void retriesStaySilentAndBusinessRejectionsArePublished() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", Money.of(AED, "0"))));
        AtomicInteger credits = new AtomicInteger();
        List<LedgerDomainEvent.ProcessingRejected> rejections = new ArrayList<>();
        engine.on(LedgerDomainEvent.CreditProcessed.class,
                event -> credits.incrementAndGet());
        engine.on(LedgerDomainEvent.ProcessingRejected.class, rejections::add);
        LedgerCommand credit = new LedgerCommand("E1", 1, 1, CREDIT,
                "A", Money.of(AED, "10"));

        engine.process(credit);
        engine.process(credit);
        LedgerValidationException error = assertThrows(LedgerValidationException.class,
                () -> engine.process(new LedgerCommand("E1", 1, 1, DEBIT,
                        "A", Money.of(AED, "10"))));

        assertEquals(CONFLICTING_EVENT_ID, error.code());
        assertEquals(1, credits.get());
        assertEquals(List.of(CONFLICTING_EVENT_ID),
                rejections.stream().map(LedgerDomainEvent.ProcessingRejected::code).toList());
        assertEquals(Money.of(AED, "10"), engine.balance("A", 1));
    }

    @Test
    void listenerFailureDoesNotUndoStateOrBlockOtherListeners() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("A", Money.of(AED, "0"))));
        AtomicInteger received = new AtomicInteger();
        engine.on(LedgerDomainEvent.CreditProcessed.class,
                event -> { throw new IllegalStateException("listener failed"); });
        engine.on(LedgerDomainEvent.CreditProcessed.class,
                event -> received.incrementAndGet());

        engine.process(new LedgerCommand("E1", 1, 1, CREDIT,
                "A", Money.of(AED, "10")));

        assertEquals(Money.of(AED, "10"), engine.balance("A", 1));
        assertEquals(1, received.get());
        assertEquals(1, engine.eventDeliveryFailures().size());
    }

    @Test
    void capitalizationPublishesOnlyNewInterestEntries() {
        LedgerEngine engine = new LedgerEngine(List.of(
                new Account("B", Money.of(BHD, "10"))));
        List<LedgerDomainEvent.InterestCapitalized> received = new ArrayList<>();
        engine.on(LedgerDomainEvent.InterestCapitalized.class, received::add);

        engine.capitalizeInterest(1, 6);
        engine.capitalizeInterest(1, 6);

        assertEquals(1, received.size());
        assertEquals(Money.of(BHD, "0.024"), received.get(0).entry().amount());
    }
}
