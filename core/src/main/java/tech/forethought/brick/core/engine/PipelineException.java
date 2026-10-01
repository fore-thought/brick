package tech.forethought.brick.core.engine;

/**
 * A run failure: spec errors, node failure (with the node's identity), or
 * loop protection (the firing cap). Messages must state exactly where the
 * problem is.
 */
public class PipelineException extends RuntimeException {

    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
