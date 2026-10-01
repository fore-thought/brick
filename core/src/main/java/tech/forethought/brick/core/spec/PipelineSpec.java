package tech.forethought.brick.core.spec;

import java.util.List;

/**
 * A graph: nodes and data wires between their pins. {@code inputs} and
 * {@code outputs} declare the external run interface — where run input is
 * injected and where callers read results; both empty means a self-contained
 * graph (e.g. a subgraph). Pure data; execution semantics live in the engine.
 * Immutable.
 *
 * @param name       graph name
 * @param nodes      the nodes
 * @param edges      the data wires between pins
 * @param inputs     pins the run injects external values into
 * @param outputs    pins callers read results from
 * @param maxFirings cap on total node firings per run: loop protection
 */
public record PipelineSpec(String name, List<NodeSpec> nodes, List<EdgeSpec> edges,
                           List<PinRef> inputs, List<PinRef> outputs, int maxFirings) {

    /** Default cap on node firings per run: loop protection. */
    public static final int DEFAULT_MAX_FIRINGS = 50;

    public PipelineSpec {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public PipelineSpec(String name, List<NodeSpec> nodes, List<EdgeSpec> edges,
                        List<PinRef> inputs, List<PinRef> outputs) {
        this(name, nodes, edges, inputs, outputs, DEFAULT_MAX_FIRINGS);
    }
}
