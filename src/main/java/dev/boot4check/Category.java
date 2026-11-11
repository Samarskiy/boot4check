package dev.boot4check;

/** How a problem shows up after the upgrade. */
public enum Category {
    /** Compiles, starts and passes smoke tests, but behaves differently. The reason this tool exists. */
    BEHAVIOR("Silent behaviour change"),
    /** Compiles, but the application or its tests fail when they start. */
    STARTUP("Fails at startup"),
    /** The build breaks, so you find out immediately. */
    BUILD("Fails the build");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
