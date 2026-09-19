package ledger.domain;

import java.util.List;

import ledger.domain.enums.LedgerEntryType;
import ledger.service.command.dto.LedgerCommandPayload;

/**
 * The single append point for booked money, and the only place entry IDs are
 * minted — <code>event:E7</code>, or <code>event:E10:2</code> for one part of a
 * multi-part booking. Idempotency depends on that convention living in one
 * method, so handlers post through here rather than constructing entries.
 */
public final class LedgerEntryBook {
    private final List<LedgerEntry> entries;

    public LedgerEntryBook(List<LedgerEntry> entries) {
        this.entries = entries;
    }

    /** Appends one entry: value day from the command, ID derived from event and part. */
    public LedgerEntry post(LedgerCommandPayload command, LedgerEntryType type, Money signedAmount,
                            String referenceId, int part, int parts) {
        String suffix = parts == 1 ? "" : ":" + part;
        LedgerEntry entry = new LedgerEntry("event:" + command.eventId() + suffix, command.eventId(),
                command.accountId(), signedAmount, command.valueDay(), type, referenceId);
        entries.add(entry);
        return entry;
    }

    /** Every entry booked by one source event, in booking order. */
    public List<LedgerEntry> bySourceEvent(String sourceEventId) {
        return entries.stream()
                .filter(entry -> entry.sourceEventId().equals(sourceEventId))
                .toList();
    }
}
