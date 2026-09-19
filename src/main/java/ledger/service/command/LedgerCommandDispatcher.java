package ledger.service.command;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ledger.domain.LedgerEntry;
import ledger.domain.exception.LedgerArgumentException;
import ledger.domain.enums.CommandType;
import ledger.domain.LedgerCommandPayload;
import ledger.service.command.handler.LedgerCommandHandler;

/**
 * Routes validated commands to the handler that declared their type, so a new
 * command type means a new handler rather than an edited switch.
 *
 * <p>A switch over an enum fails to compile when a constant has no branch; a
 * registry cannot, so construction rejects duplicate and missing handlers
 * instead. Every engine builds a dispatcher, so a gap surfaces on the first
 * construction rather than on the command that needed it.
 */
public final class LedgerCommandDispatcher {
    private final Map<CommandType, LedgerCommandHandler> handlers = new EnumMap<>(CommandType.class);
    private final Map<String, LedgerCommandPayload> processedCommands;

    public LedgerCommandDispatcher(List<LedgerCommandHandler> commandHandlers,
                                   Map<String, LedgerCommandPayload> processedCommands) {
        for (LedgerCommandHandler handler : commandHandlers) {
            if (handlers.put(handler.handles(), handler) != null) {
                throw new LedgerArgumentException("Duplicate handler for " + handler.handles());
            }
        }
        for (CommandType type : CommandType.values()) {
            if (!handlers.containsKey(type)) {
                throw new LedgerArgumentException("No handler for " + type);
            }
        }
        this.processedCommands = processedCommands;
    }

    /**
     * Returns the entries the command booked, empty when it booked none. The
     * command is recorded only after its handler succeeds.
     */
    public List<LedgerEntry> dispatch(LedgerCommandPayload command) {
        List<LedgerEntry> booked = handlers.get(command.type()).handle(command);
        processedCommands.put(command.eventId(), command);
        return booked;
    }
}
