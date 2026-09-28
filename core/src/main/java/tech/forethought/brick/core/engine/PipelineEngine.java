package tech.forethought.brick.core.engine;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.Services;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;

/**
 * The graph engine — a pure caller. It walks the spec's edges, invokes each
 * node, and routes by matching edge labels against the route key left by
 * gateway nodes. It understands no business logic.
 *
 * <p>Thread-safe; holds no run state between calls. Sequential execution;
 * parallel branches are a planned enhancement the topology already permits.
 */
public final class PipelineEngine {

    private final Services services;

    public PipelineEngine(Services services) {
        this.services = Objects.requireNonNull(services);
    }

    /**
     * Runs the spec from its entry node until a terminal node (no out-edges).
     *
     * @param input initial edge data; not modified
     * @return the final edge data
     * @throws PipelineException on spec errors, node failure, unroutable
     *         transitions, or when the iteration cap is hit
     */
    public Map<String, Object> run(PipelineSpec spec, Map<String, Object> input) {
        var errors = SpecValidator.validate(spec).stream()
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new PipelineException("spec has errors: " + errors);
        }
        var nodesById = new HashMap<String, NodeSpec>();
        for (var node : spec.nodes()) {
            nodesById.put(node.id(), node);
        }

        var runId = UUID.randomUUID().toString();
        var data = Map.copyOf(input);
        var currentId = spec.entryNodeId();
        var iterations = 0;
        while (currentId != null) {
            if (++iterations > spec.maxIterations()) {
                throw new PipelineException(
                        "loop protection: exceeded maxIterations=" + spec.maxIterations());
            }
            var nodeSpec = nodesById.get(currentId);
            var context = new NodeContext(runId, nodeSpec.id(), nodeSpec.config(), services);
            Map<String, Object> output;
            try {
                var node = services.require(Node.class, nodeSpec.type());
                output = node.execute(data, context);
            } catch (Exception e) {
                throw new PipelineException("node '" + nodeSpec.id() + "' (type '"
                        + nodeSpec.type() + "') failed: " + e.getMessage(), e);
            }
            if (output == null) {
                throw new PipelineException("node '" + nodeSpec.id() + "' returned null");
            }
            data = Map.copyOf(output);
            var nextId = nextNode(spec, currentId, data);
            if (nextId != null) {
                data = consumeRoute(data);
            }
            currentId = nextId;
        }
        return data;
    }

    private String nextNode(PipelineSpec spec, String currentId, Map<String, Object> data) {
        List<EdgeSpec> out = spec.edges().stream()
                .filter(e -> e.from().equals(currentId))
                .toList();
        if (out.isEmpty()) {
            return null;
        }
        var route = data.get(EdgeKeys.ROUTE);
        if (route != null) {
            for (var edge : out) {
                if (Objects.equals(edge.label(), String.valueOf(route))) {
                    return edge.to();
                }
            }
        }
        for (var edge : out) {
            if (edge.label() == null) {
                return edge.to();
            }
        }
        throw new PipelineException(
                "no route from node '" + currentId + "' matches route value '" + route + "'");
    }

    private static Map<String, Object> consumeRoute(Map<String, Object> data) {
        if (!data.containsKey(EdgeKeys.ROUTE)) {
            return data;
        }
        var stripped = new LinkedHashMap<>(data);
        stripped.remove(EdgeKeys.ROUTE);
        return Map.copyOf(stripped);
    }
}
