package ledger.service.command.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ledger.domain.LedgerEntry;
import ledger.domain.enums.LedgerEntryType;
import ledger.domain.exception.LedgerValidationException;
import ledger.service.command.LedgerEntryBook;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;

import static ledger.domain.exception.LedgerValidationException.Code.EVENT_ACCOUNT_MISMATCH;
import static ledger.domain.exception.LedgerValidationException.Code.EVENT_ALREADY_REVERSED;
import static ledger.domain.exception.LedgerValidationException.Code.EVENT_NOT_REVERSIBLE;
import static ledger.domain.exception.LedgerValidationException.Code.UNKNOWN_EVENT;

/**
 * Appends the exact opposite of every entry booked by the referenced event —
 * one reversal entry per original, each referencing it. Unknown, cross-account,
 * repeated and nonfinancial references are rejected before anything is written,
 * and an event can only ever be reversed once.
 */
public final class ReversalHandler implements LedgerCommandHandler {
    private final LedgerEntryBook entryBook;
    private final Map<String, LedgerCommandPayload> processedCommands;
    private final Set<String> reversedEventIds = new HashSet<>();

    public ReversalHandler(LedgerEntryBook entryBook,
                           Map<String, LedgerCommandPayload> processedCommands) {
        this.entryBook = entryBook;
        this.processedCommands = processedCommands;
    }

    @Override
    public CommandType handles() {
        return CommandType.REVERSAL;
    }

    @Override
    public List<LedgerEntry> handle(LedgerCommandPayload command) {
        LedgerCommandPayload original = processedCommands.get(command.referencedEventId());
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
        List<LedgerEntry> originals = reversible(command.referencedEventId());
        if (originals.isEmpty()) {
            throw new LedgerValidationException(EVENT_NOT_REVERSIBLE,
                    "Event has no reversible ledger movement: " + command.referencedEventId());
        }
        List<LedgerEntry> posted = new ArrayList<>(originals.size());
        for (int index = 0; index < originals.size(); index++) {
            LedgerEntry originalEntry = originals.get(index);
            posted.add(entryBook.post(command, LedgerEntryType.REVERSAL,
                    originalEntry.amount().negate(), originalEntry.entryId(),
                    index + 1, originals.size()));
        }
        reversedEventIds.add(command.referencedEventId());
        return List.copyOf(posted);
    }

    /** Only booked money reverses: derived fees and interest are not compensated here. */
    private List<LedgerEntry> reversible(String sourceEventId) {
        return entryBook.bySourceEvent(sourceEventId).stream()
                .filter(entry -> entry.type() == LedgerEntryType.CREDIT
                        || entry.type() == LedgerEntryType.DEBIT
                        || entry.type() == LedgerEntryType.SETTLEMENT)
                .toList();
    }
}
