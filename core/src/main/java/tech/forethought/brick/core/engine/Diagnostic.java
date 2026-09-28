package tech.forethought.brick.core.engine;

/**
 * One problem found by {@link SpecValidator}: severity, where it is, and
 * what it says. Diagnostics inform; they never block on their own.
 * Immutable.
 */
public record Diagnostic(Severity severity, String location, String message) {

    /** Severity of a diagnostic. */
    public enum Severity {
        /** Prevents a run. */
        ERROR,
        /** Suspicious but runnable. */
        WARNING
    }
}
