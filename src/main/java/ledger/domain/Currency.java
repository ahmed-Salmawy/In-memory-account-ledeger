package ledger.domain;

public enum Currency {
    AED(2),
    BHD(3);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }
}
