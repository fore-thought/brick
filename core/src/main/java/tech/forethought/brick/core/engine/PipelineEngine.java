package tech.forethought.brick.core.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tech.forethought.brick.core.event.EventEmitter;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.event.EventListener;
import tech.forethought.brick.core.event.TraceEvent;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.Services;

/**
 * The graph engine — a pure caller. It walks the spec's edges, invokes each
 * node, and routes by matching edge labels against the route key left by
 * gateway nodes. It understands no business logic.
 *
 * <p>Every fact of the run is emitted onto an append-only event stream:
 * observation, persistence, and live display are all listeners. Emission is
 * synchronous; a failing listener never affects the run or other listeners.
 * Payloads are redacted for sensitive keys.
 *
 * <p>Thread-safe: holds no run state between calls. Sequential execution;
 * parallel branches are a planned enhancement the topology already permits.
 */
public final class PipelineEngine {

    private final Services services;
    private final List<EventListener> listeners;
    private final boolean debugSnapshots;
    private final Set<String> extraSensitiveKeys;

    public PipelineEngine(Services services) {
        this(services, new EngineConfig());
    }

    public PipelineEngine(Services services, EngineConfig config) {
        this.services = Objects.requireNonNull(services);
        var all = new ArrayList<EventListener>(config.listeners());
        all.addAll(services.all(EventListener.class));
        this.listeners = List.copyOf(all);
        this.debugSnapshots = config.debugSnapshots();
        this.extraSensitiveKeys = config.extraSensitiveKeys();
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
        var sessionId = Objects.toString(input.get(EdgeKeys.SESSION_ID), "default");
        var data = Map.copyOf(input);
        var iterations = 0;
        emit(EventKinds.RUN_START, runId, sessionId, null, Map.of("spec", spec.name()));
        var currentId = spec.entryNodeId();
        try {
            while (currentId != null) {
                if (++iterations > spec.maxIterations()) {
                    throw new PipelineException(
                            "loop protection: exceeded maxIterations=" + spec.maxIterations());
                }
                var nodeSpec = nodesById.get(currentId);
                emit(EventKinds.NODE_ENTER, runId, sessionId, currentId, payloadOf(data));
                var emitter = new NodeEmitter(runId, sessionId, currentId);
                var context = new NodeContext(runId, nodeSpec.id(), nodeSpec.config(), services,
                        emitter);
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
                emit(EventKinds.NODE_EXIT, runId, sessionId, currentId, payloadOf(data));
                var nextId = nextNode(spec, currentId, data);
                if (nextId != null) {
                    data = consumeRoute(data);
                }
                currentId = nextId;
            }
            emit(EventKinds.RUN_END, runId, sessionId, null,
                    Map.of("status", "ok", "iterations", iterations));
            return data;
        } catch (PipelineException e) {
            emit(EventKinds.RUN_END, runId, sessionId, null,
                    Map.of("status", "error", "error", String.valueOf(e.getMessage())));
            throw e;
        }
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

    private Map<String, Object> payloadOf(Map<String, Object> data) {
        if (!debugSnapshots) {
            var summary = new LinkedHashMap<String, Object>();
            for (var entry : data.entrySet()) {
                var value = entry.getValue();
                summary.put(entry.getKey(),
                        value == null ? "null" : value.getClass().getSimpleName());
            }
            return Map.copyOf(summary);
        }
        return redact(data);
    }

    /** Redacts sensitive keys at any depth; over-masking is intentional. */
    private Map<String, Object> redact(Map<String, Object> data) {
        var out = new LinkedHashMap<String, Object>();
        for (var entry : data.entrySet()) {
            out.put(entry.getKey(), redactValue(entry.getKey(), entry.getValue()));
        }
        return Map.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private Object redactValue(String key, Object value) {
        if (isSensitive(key)) {
            return "***";
        }
        if (value instanceof Map<?, ?> map) {
            var out = new LinkedHashMap<String, Object>();
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                out.put(entry.getKey(), redactValue(entry.getKey(), entry.getValue()));
            }
            return Map.copyOf(out);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof Map<?, ?> ? redactValue("", item) : item)
                    .toList();
        }
        return value;
    }

    private boolean isSensitive(String key) {
        var lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("key") || lower.contains("token") || lower.contains("secret")
                || lower.contains("password") || extraSensitiveKeys.contains(key);
    }

    private void emit(String kind, String runId, String sessionId, String nodeId,
                      Map<String, Object> payload) {
        broadcast(new TraceEvent(kind, System.currentTimeMillis(), runId, sessionId, nodeId,
                redact(payload)));
    }

    private void broadcast(TraceEvent event) {
        for (var listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                System.err.println("brick: listener " + listener.getClass().getName()
                        + " failed on " + event.kind() + ": " + e.getMessage());
            }
        }
    }

    /** Node-facing emitter: stamps run/node identity onto node-emitted events. */
    private final class NodeEmitter implements EventEmitter {

        private final String runId;
        private final String sessionId;
        private final String nodeId;

        private NodeEmitter(String runId, String sessionId, String nodeId) {
            this.runId = runId;
            this.sessionId = sessionId;
            this.nodeId = nodeId;
        }

        @Override
        public void emit(String kind, Map<String, Object> payload) {
            PipelineEngine.this.emit(kind, runId, sessionId, nodeId, payload);
        }
    }
}
