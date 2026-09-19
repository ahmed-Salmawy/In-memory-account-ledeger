package ledger.service.command.handler;

import java.util.List;
import ledger.domain.LedgerEntry;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;

/**
 * Handling for exactly one command type, declared by the handler itself so the
 * dispatcher routes by registration instead of a switch.
 */
public interface LedgerCommandHandler {
    /** The one command type this handler is registered for. */
    CommandType handles();

    /**
     * Applies the validated command and returns the entries it booked, in
     * booking order. An empty list means the command changed no balance — an
     * authorization reserves funds without booking money — which is what tells
     * the engine no fee reconciliation is owed.
     */
    List<LedgerEntry> handle(LedgerCommandPayload command);
}
