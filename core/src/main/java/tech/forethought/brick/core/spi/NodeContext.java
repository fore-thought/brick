package tech.forethought.brick.core.spi;

import java.util.Map;

/**
 * Per-execution context handed to a node: run identity, the node's own id
 * and configuration from the spec, and service lookup. Immutable.
 */
public record NodeContext(String runId, String nodeId, Map<String, Object> config,
                          Services services) {

    public NodeContext {
        config = Map.copyOf(config);
    }
}
