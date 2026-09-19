package ledger.domain.enums;

/** Supported account currencies with their exact minor-unit scales. */
public enum Currency {
    /** UAE dirham — two decimal places; the smallest representable amount is 0.01. */
    AED(2),
    /** Bahraini dinar — three decimal places; the smallest representable amount is 0.001. */
    BHD(3);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    /** Decimal places every <code>Money</code> of this currency must carry. */
    public int scale() {
        return scale;
    }
}
