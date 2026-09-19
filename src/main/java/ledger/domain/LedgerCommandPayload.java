package ledger.domain;

import java.util.Objects;

import ledger.domain.enums.CommandType;
import ledger.domain.exception.LedgerArgumentException;

/** Instruction to change ledger state; successful handling returns the entries it booked. */
public record LedgerCommandPayload(String eventId, int postedDay, int valueDay,
                                   CommandType type, String accountId, Money amount, String authorizationId,
                                   String referencedEventId, int installmentCount) {
    public LedgerCommandPayload(String eventId, int postedDay, int valueDay,
                         CommandType type, String accountId, Money amount) {
        this(eventId, postedDay, valueDay, type, accountId, amount, null, null, 1);
    }

    public LedgerCommandPayload(String eventId, int postedDay, int valueDay,
                         CommandType type, String accountId, Money amount, String authorizationId) {
        this(eventId, postedDay, valueDay, type, accountId, amount, authorizationId, null, 1);
    }

    public static LedgerCommandPayload reversal(String eventId, int postedDay, int valueDay,
                                         String accountId, String referencedEventId) {
        return new LedgerCommandPayload(eventId, postedDay, valueDay, CommandType.REVERSAL,
                accountId, null, null, referencedEventId, 1);
    }

    public static LedgerCommandPayload allocatedCredit(String eventId, int postedDay, int valueDay,
                                                String accountId, Money amount, int installmentCount) {
        return new LedgerCommandPayload(eventId, postedDay, valueDay, CommandType.CREDIT,
                accountId, amount, null, null, installmentCount);
    }

    public LedgerCommandPayload {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(accountId, "accountId");
        if (eventId.isBlank() || accountId.isBlank()) {
            throw new LedgerArgumentException("Event and account IDs must not be blank");
        }
        if (postedDay < 1 || valueDay < 1) {
            throw new LedgerArgumentException("Business days must be positive");
        }
        if (valueDay > postedDay) {
            throw new LedgerArgumentException("Value day must not be later than the posted day");
        }
        if (type == CommandType.REVERSAL) {
            if (amount != null || authorizationId != null || referencedEventId == null
                    || referencedEventId.isBlank() || installmentCount != 1) {
                throw new LedgerArgumentException("Reversal must reference one event and carry no amount");
            }
        } else {
            Objects.requireNonNull(amount, "amount");
            if (amount.amount().signum() <= 0) {
                throw new LedgerArgumentException("Input amount must be positive");
            }
            if (referencedEventId != null) {
                throw new LedgerArgumentException("Only reversal events may reference an event");
            }
            if (type == CommandType.AUTHORIZATION || type == CommandType.SETTLEMENT) {
                if (authorizationId == null || authorizationId.isBlank() || installmentCount != 1) {
                    throw new LedgerArgumentException("Authorization reference is required");
                }
            } else if (authorizationId != null) {
                throw new LedgerArgumentException("Only authorization and settlement commands may carry an authorization ID");
            }
            if ((type != CommandType.CREDIT && installmentCount != 1) || installmentCount < 1) {
                throw new LedgerArgumentException("Only credits may have multiple instalments");
            }
        }
    }
}
