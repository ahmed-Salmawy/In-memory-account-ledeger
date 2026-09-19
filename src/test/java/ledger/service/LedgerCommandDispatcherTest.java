package ledger.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import ledger.domain.LedgerEntry;
import ledger.domain.exception.LedgerArgumentException;
import ledger.service.command.LedgerCommandDispatcher;
import ledger.service.command.dto.CommandType;
import ledger.service.command.dto.LedgerCommandPayload;
import ledger.service.command.handler.LedgerCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The registry replaced an exhaustive enum switch, so its construction-time
 * guard is what now catches an unhandled command type.
 */
class LedgerCommandDispatcherTest {
    @Test
    @DisplayName("A command type left without a handler is rejected at construction")
    void missingHandlerIsRejected() {
        List<LedgerCommandHandler> handlers = new ArrayList<>();
        for (CommandType type : CommandType.values()) {
            if (type != CommandType.REVERSAL) {
                handlers.add(stub(type));
            }
        }
        LedgerArgumentException thrown = assertThrows(LedgerArgumentException.class,
                () -> new LedgerCommandDispatcher(handlers, new LinkedHashMap<>()));
        assertEquals("No handler for REVERSAL", thrown.getMessage());
    }

    @Test
    @DisplayName("Two handlers claiming one command type are rejected at construction")
    void duplicateHandlerIsRejected() {
        List<LedgerCommandHandler> handlers = List.of(stub(CommandType.CREDIT), stub(CommandType.CREDIT));
        LedgerArgumentException thrown = assertThrows(LedgerArgumentException.class,
                () -> new LedgerCommandDispatcher(handlers, new LinkedHashMap<>()));
        assertEquals("Duplicate handler for CREDIT", thrown.getMessage());
    }

    private static LedgerCommandHandler stub(CommandType type) {
        return new LedgerCommandHandler() {
            @Override
            public CommandType handles() {
                return type;
            }

            @Override
            public List<LedgerEntry> handle(LedgerCommandPayload command) {
                throw new UnsupportedOperationException("no command is dispatched in this test");
            }
        };
    }
}
