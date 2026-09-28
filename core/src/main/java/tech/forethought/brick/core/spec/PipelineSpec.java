package tech.forethought.brick.core.spec;

import java.util.List;

/**
 * A graph: nodes, directed edges, an entry node, and a loop-protection cap.
 * Pure data; execution semantics live in the engine. Immutable.
 */
public record PipelineSpec(String name, List<NodeSpec> nodes, List<EdgeSpec> edges,
                           String entryNodeId, int maxIterations) {

    /** Default cap on node executions per run: loop protection. */
    public static final int DEFAULT_MAX_ITERATIONS = 50;

    public PipelineSpec {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public PipelineSpec(String name, List<NodeSpec> nodes, List<EdgeSpec> edges, String entryNodeId) {
        this(name, nodes, edges, entryNodeId, DEFAULT_MAX_ITERATIONS);
    }
}
