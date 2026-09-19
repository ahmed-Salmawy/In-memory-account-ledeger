package ledger.service.command.handler;

import java.util.List;
import ledger.domain.LedgerEntry;
import ledger.domain.enums.LedgerEntryType;
import ledger.service.command.LedgerEntryBook;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;

/** Books a debit as a negative entry — debits may overdraw the account. */
public final class DebitHandler implements LedgerCommandHandler {
    private final LedgerEntryBook entryBook;

    public DebitHandler(LedgerEntryBook entryBook) {
        this.entryBook = entryBook;
    }

    @Override
    public CommandType handles() {
        return CommandType.DEBIT;
    }

    @Override
    public List<LedgerEntry> handle(LedgerCommandPayload command) {
        return List.of(entryBook.post(command, LedgerEntryType.DEBIT,
                command.amount().negate(), null, 1, 1));
    }
}
