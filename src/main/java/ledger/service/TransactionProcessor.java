package ledger.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ledger.domain.LedgerValidationException;
import ledger.domain.Money;
import ledger.domain.LedgerEntry;
import ledger.domain.LedgerEntryType;
import ledger.domain.LedgerCommand;

import static ledger.domain.LedgerValidationException.Code.EVENT_ACCOUNT_MISMATCH;
import static ledger.domain.LedgerValidationException.Code.EVENT_ALREADY_REVERSED;
import static ledger.domain.LedgerValidationException.Code.EVENT_NOT_REVERSIBLE;
import static ledger.domain.LedgerValidationException.Code.UNKNOWN_EVENT;

public final class TransactionProcessor {
    private final List<LedgerEntry> entries;
    private final Map<String, LedgerCommand> processedCommands;
    private final Set<String> reversedEventIds = new HashSet<>();

    public TransactionProcessor(List<LedgerEntry> entries, Map<String, LedgerCommand> processedCommands) {
        this.entries = entries;
        this.processedCommands = processedCommands;
    }

    public List<LedgerEntry> credit(LedgerCommand command) {
        List<Money> amounts = command.amount().allocate(command.installmentCount());
        List<LedgerEntry> posted = new java.util.ArrayList<>(amounts.size());
        for (int index = 0; index < amounts.size(); index++) {
            posted.add(post(command, LedgerEntryType.CREDIT, amounts.get(index), null,
                    index + 1, amounts.size()));
        }
        return List.copyOf(posted);
    }

    public LedgerEntry debit(LedgerCommand command) {
        return post(command, LedgerEntryType.DEBIT, command.amount().negate(), null, 1, 1);
    }

    public LedgerEntry settlement(LedgerCommand command) {
        return post(command, LedgerEntryType.SETTLEMENT, command.amount().negate(),
                command.authorizationId(), 1, 1);
    }

    public List<LedgerEntry> reverse(LedgerCommand command) {
        LedgerCommand original = processedCommands.get(command.referencedEventId());
        if (original == null) {
            throw new LedgerValidationException(UNKNOWN_EVENT,
                    "Unknown event: " + command.referencedEventId());
        }
        if (!original.accountId().equals(command.accountId())) {
            throw new LedgerValidationException(EVENT_ACCOUNT_MISMATCH,
                    "Referenced event belongs to another account: " + command.referencedEventId());
        }
        if (reversedEventIds.contains(command.referencedEventId())) {
            throw new LedgerValidationException(EVENT_ALREADY_REVERSED,
                    "Event already reversed: " + command.referencedEventId());
        }
        List<LedgerEntry> originals = entries.stream()
                .filter(entry -> entry.sourceEventId().equals(command.referencedEventId()))
                .filter(entry -> entry.type() == LedgerEntryType.CREDIT
                        || entry.type() == LedgerEntryType.DEBIT
                        || entry.type() == LedgerEntryType.SETTLEMENT)
                .toList();
        if (originals.isEmpty()) {
            throw new LedgerValidationException(EVENT_NOT_REVERSIBLE,
                    "Event has no reversible ledger movement: " + command.referencedEventId());
        }
        List<LedgerEntry> posted = new java.util.ArrayList<>(originals.size());
        for (int index = 0; index < originals.size(); index++) {
            LedgerEntry originalEntry = originals.get(index);
            posted.add(post(command, LedgerEntryType.REVERSAL, originalEntry.amount().negate(),
                    originalEntry.entryId(), index + 1, originals.size()));
        }
        reversedEventIds.add(command.referencedEventId());
        return List.copyOf(posted);
    }

    private LedgerEntry post(LedgerCommand command, LedgerEntryType type, Money signedAmount,
                             String referenceId, int part, int parts) {
        String suffix = parts == 1 ? "" : ":" + part;
        LedgerEntry entry = new LedgerEntry("event:" + command.eventId() + suffix, command.eventId(),
                command.accountId(), signedAmount, command.valueDay(), type, referenceId);
        entries.add(entry);
        return entry;
    }
}
