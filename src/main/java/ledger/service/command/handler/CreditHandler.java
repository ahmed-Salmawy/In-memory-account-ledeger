package ledger.service.command.handler;

import java.util.ArrayList;
import java.util.List;
import ledger.domain.LedgerEntry;
import ledger.domain.Money;
import ledger.domain.enums.LedgerEntryType;
import ledger.domain.LedgerEntryBook;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;

/** Books a credit as one entry per instalment, splitting exact minor units. */
public final class CreditHandler implements LedgerCommandHandler {
    private final LedgerEntryBook entryBook;

    public CreditHandler(LedgerEntryBook entryBook) {
        this.entryBook = entryBook;
    }

    @Override
    public CommandType handles() {
        return CommandType.CREDIT;
    }

    @Override
    public List<LedgerEntry> handle(LedgerCommandPayload command) {
        List<Money> amounts = command.amount().allocate(command.installmentCount());
        List<LedgerEntry> posted = new ArrayList<>(amounts.size());
        for (int index = 0; index < amounts.size(); index++) {
            posted.add(entryBook.post(command, LedgerEntryType.CREDIT, amounts.get(index), null,
                    index + 1, amounts.size()));
        }
        return List.copyOf(posted);
    }
}
