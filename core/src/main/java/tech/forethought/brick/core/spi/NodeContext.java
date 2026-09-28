package tech.forethought.brick.core.spi;

import java.util.Map;
import tech.forethought.brick.core.event.EventEmitter;

/**
 * Per-execution context handed to a node: run identity, the node's own id
 * and configuration from the spec, service lookup, and the event emitter.
 * Immutable.
 */
public record NodeContext(String runId, String nodeId, Map<String, Object> config,
                          Services services, EventEmitter events) {

    public NodeContext {
        config = Map.copyOf(config);
    }
}
