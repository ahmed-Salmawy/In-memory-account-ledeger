package ledger.domain;

import java.util.List;
import java.util.Objects;

/** Immutable facts published after ledger state has been updated. */
public interface LedgerDomainEvent {
    interface MovementProcessed extends LedgerDomainEvent {
        LedgerCommand command();

        List<LedgerEntry> entries();
    }

    record CreditProcessed(LedgerCommand command, List<LedgerEntry> entries) implements MovementProcessed {
        public CreditProcessed {
            Objects.requireNonNull(command, "command");
            entries = List.copyOf(entries);
        }
    }

    record DebitProcessed(LedgerCommand command, List<LedgerEntry> entries) implements MovementProcessed {
        public DebitProcessed {
            Objects.requireNonNull(command, "command");
            entries = List.copyOf(entries);
        }
    }

    record SettlementProcessed(LedgerCommand command, List<LedgerEntry> entries,
                               Authorization authorization) implements MovementProcessed {
        public SettlementProcessed {
            Objects.requireNonNull(command, "command");
            entries = List.copyOf(entries);
            Objects.requireNonNull(authorization, "authorization");
        }
    }

    record ReversalProcessed(LedgerCommand command, List<LedgerEntry> entries) implements MovementProcessed {
        public ReversalProcessed {
            Objects.requireNonNull(command, "command");
            entries = List.copyOf(entries);
        }
    }

    record AuthorizationProcessed(LedgerCommand command,
                                  Authorization authorization) implements LedgerDomainEvent {
        public AuthorizationProcessed {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(authorization, "authorization");
        }
    }

    record OverdraftFeeProcessed(LedgerEntry entry) implements LedgerDomainEvent {
        public OverdraftFeeProcessed {
            Objects.requireNonNull(entry, "entry");
        }
    }

    record InterestCapitalized(LedgerEntry entry) implements LedgerDomainEvent {
        public InterestCapitalized {
            Objects.requireNonNull(entry, "entry");
        }
    }

    record ProcessingRejected(LedgerCommand command, LedgerValidationException.Code code,
                              String message) implements LedgerDomainEvent {
        public ProcessingRejected {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
