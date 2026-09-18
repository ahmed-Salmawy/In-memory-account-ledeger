package ledger.domain;

import java.util.Objects;

/** Instruction to change ledger state; successful handling emits a domain event. */
public record LedgerCommand(String eventId, int postedDay, int valueDay,
                            CommandType type, String accountId, Money amount, String authorizationId,
                            String referencedEventId, int installmentCount) {
    public LedgerCommand(String eventId, int postedDay, int valueDay,
                         CommandType type, String accountId, Money amount) {
        this(eventId, postedDay, valueDay, type, accountId, amount, null, null, 1);
    }

    public LedgerCommand(String eventId, int postedDay, int valueDay,
                         CommandType type, String accountId, Money amount, String authorizationId) {
        this(eventId, postedDay, valueDay, type, accountId, amount, authorizationId, null, 1);
    }

    public static LedgerCommand reversal(String eventId, int postedDay, int valueDay,
                                         String accountId, String referencedEventId) {
        return new LedgerCommand(eventId, postedDay, valueDay, CommandType.REVERSAL,
                accountId, null, null, referencedEventId, 1);
    }

    public static LedgerCommand allocatedCredit(String eventId, int postedDay, int valueDay,
                                                String accountId, Money amount, int installmentCount) {
        return new LedgerCommand(eventId, postedDay, valueDay, CommandType.CREDIT,
                accountId, amount, null, null, installmentCount);
    }

    public LedgerCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(accountId, "accountId");
        if (eventId.isBlank() || accountId.isBlank()) {
            throw new IllegalArgumentException("Event and account IDs must not be blank");
        }
        if (postedDay < 1 || valueDay < 1) {
            throw new IllegalArgumentException("Business days must be positive");
        }
        if (type == CommandType.REVERSAL) {
            if (amount != null || authorizationId != null || referencedEventId == null
                    || referencedEventId.isBlank() || installmentCount != 1) {
                throw new IllegalArgumentException("Reversal must reference one event and carry no amount");
            }
        } else {
            Objects.requireNonNull(amount, "amount");
            if (amount.amount().signum() <= 0) {
                throw new IllegalArgumentException("Input amount must be positive");
            }
            if (referencedEventId != null) {
                throw new IllegalArgumentException("Only reversal events may reference an event");
            }
            if (type == CommandType.AUTHORIZATION || type == CommandType.SETTLEMENT) {
                if (authorizationId == null || authorizationId.isBlank() || installmentCount != 1) {
                    throw new IllegalArgumentException("Authorization reference is required");
                }
            } else if (authorizationId != null) {
                throw new IllegalArgumentException("Only authorization and settlement commands may carry an authorization ID");
            }
            if ((type != CommandType.CREDIT && installmentCount != 1) || installmentCount < 1) {
                throw new IllegalArgumentException("Only credits may have multiple instalments");
            }
        }
    }
}
