package tech.forethought.brick.core.spec;

import java.util.Map;

/**
 * One node on the graph: identity, the node type selecting a {@code Node}
 * implementation, and its configuration. Immutable.
 *
 * @param id     unique within one spec; how edges reference this node
 * @param type   the node type (e.g. {@code "if"}), resolved to a {@code Node}
 *               implementation at assembly
 * @param config node-specific configuration
 */
public record NodeSpec(String id, String type, Map<String, Object> config) {

    public NodeSpec {
        config = Map.copyOf(config);
    }
}
