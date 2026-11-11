package dev.boot4check;

/** How sure we are that a finding is a real problem in this code base. */
public enum Confidence {
    /** Stated directly in the official migration guide and matched precisely. */
    HIGH,
    /** Documented change, but the match is a heuristic and may be a false positive. */
    MEDIUM
}
