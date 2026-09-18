package ledger.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import ledger.domain.LedgerDomainEvent;

/** Ordered, single-threaded publication; nested events wait behind the current event. */
public final class InMemoryEventPublisher {
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Deque<LedgerDomainEvent> pending = new ArrayDeque<>();
    private final List<DeliveryFailure> failures = new ArrayList<>();
    private boolean publishing;

    public <E extends LedgerDomainEvent> void subscribe(Class<E> eventType,
                                                        Consumer<? super E> subscriber) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(subscriber, "subscriber");
        subscriptions.add(new Subscription(eventType,
                event -> subscriber.accept(eventType.cast(event))));
    }

    public void publish(LedgerDomainEvent event) {
        pending.addLast(Objects.requireNonNull(event, "event"));
        if (publishing) {
            return;
        }
        publishing = true;
        try {
            while (!pending.isEmpty()) {
                LedgerDomainEvent next = pending.removeFirst();
                for (Subscription subscription : List.copyOf(subscriptions)) {
                    if (subscription.eventType().isInstance(next)) {
                        try {
                            subscription.subscriber().accept(next);
                        } catch (RuntimeException exception) {
                            failures.add(new DeliveryFailure(next, exception));
                        }
                    }
                }
            }
        } finally {
            publishing = false;
        }
    }

    public List<DeliveryFailure> failures() {
        return List.copyOf(failures);
    }

    private record Subscription(Class<?> eventType, Consumer<Object> subscriber) {
    }

    public record DeliveryFailure(LedgerDomainEvent event, RuntimeException cause) {
    }
}
