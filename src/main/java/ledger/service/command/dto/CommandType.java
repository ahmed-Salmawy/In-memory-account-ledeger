package ledger.service.command.dto;

/**
 * Input command kinds. CREDIT/DEBIT/SETTLEMENT/REVERSAL book ledger movements;
 * AUTHORIZATION only reserves spending capacity and never touches the ledger.
 */
public enum CommandType {
    CREDIT,
    DEBIT,
    AUTHORIZATION,
    SETTLEMENT,
    REVERSAL
}
