package tech.forethought.brick.core.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.Services;

/**
 * The dataflow engine — a pure caller. Edges are bindings: a value a node
 * writes to an output pin is delivered, sticky, to every wired input pin.
 * A node fires once all pins its contract declares as reads carry a
 * binding, and re-fires when any of them receives a new value; nodes with
 * no reads fire once at the start. A run ends silently when no node can
 * fire; the firing cap bounds runaway loops. It understands no business
 * logic.
 *
 * <p>Every fact of the run is emitted onto an append-only event stream:
 * observation, persistence, and live display are all listeners. Emission is
 * synchronous; a failing listener never affects the run or other listeners.
 * Payloads are redacted for sensitive keys.
 *
 * <p>Thread-safe: holds no run state between calls. Sequential execution;
 * the trigger queue is the future seam for parallel branch scheduling.
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
     * Runs the graph until no node can fire (silent termination) or the
     * firing cap is hit.
     *
     * @param input initial bindings, keyed by pin; typically the values for
     *              the spec's declared {@code inputs}
     * @return all pin bindings at termination — declared {@code outputs} are
     *         read from this map
     * @throws PipelineException on spec errors, node failure (with the node's
     *         identity), or when the firing cap is hit
     */
    public Map<PinRef, Object> run(PipelineSpec spec, Map<PinRef, Object> input) {
        return run(spec, input, "default");
    }

    /**
     * Runs the graph with an explicit session identity (see {@link #run
     * (PipelineSpec, Map)}).
     */
    public Map<PinRef, Object> run(PipelineSpec spec, Map<PinRef, Object> input,
                                   String sessionId) {
        var errors = SpecValidator.validate(spec).stream()
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new PipelineException("spec has errors: " + errors);
        }

        var runId = UUID.randomUUID().toString();
        emit(EventKinds.RUN_START, runId, sessionId, null, Map.of("spec", spec.name()));
        try {
            var assembly = assemble(spec);
            var bindings = new LinkedHashMap<PinRef, Object>();
            // the last value each node wrote under each key, even when no edge
            // consumes it: what the run result reports for the node's own pins
            var ownWrites = new LinkedHashMap<PinRef, Object>();
            var pinVersion = new HashMap<PinRef, Integer>();
            var firedVersion = new HashMap<String, Integer>();
            var fired = new HashSet<String>();
            var queue = new ArrayDeque<String>();
            var queued = new HashSet<String>();
            input.forEach((pin, value) -> bind(bindings, pinVersion, pin, value));

            var firings = 0;
            enqueueReady(spec, assembly, bindings, pinVersion, firedVersion, fired, queue,
                    queued);
            while (!queue.isEmpty()) {
                var id = queue.poll();
                queued.remove(id);
                if (++firings > spec.maxFirings()) {
                    throw new PipelineException(
                            "loop protection: exceeded maxFirings=" + spec.maxFirings());
                }
                var nodeSpec = assembly.nodesById().get(id);
                var trigger = triggerInput(id, assembly.readsByNode().get(id), bindings);
                emit(EventKinds.NODE_ENTER, runId, sessionId, id, payloadOf(trigger));
                var emitter = new NodeEmitter(runId, sessionId, id);
                var context = new NodeContext(runId, nodeSpec.id(), nodeSpec.config(), services,
                        emitter);
                Map<String, Object> output;
                try {
                    var raw = assembly.nodeImpls().get(id).execute(trigger, context);
                    if (raw == null) {
                        throw new PipelineException("node '" + nodeSpec.id() + "' (type '"
                                + nodeSpec.type() + "') returned null");
                    }
                    output = Map.copyOf(raw);
                } catch (PipelineException e) {
                    throw e;
                } catch (Exception e) {
                    throw new PipelineException("node '" + nodeSpec.id() + "' (type '"
                            + nodeSpec.type() + "') failed: " + e.getMessage(), e);
                }
                fired.add(id);
                firedVersion.put(id, readVersion(id, assembly.readsByNode().get(id), pinVersion));
                emit(EventKinds.NODE_EXIT, runId, sessionId, id, payloadOf(output));
                for (var entry : output.entrySet()) {
                    var from = new PinRef(id, entry.getKey());
                    // own writes never count as new input: a node that writes
                    // a pin it also reads must not re-trigger itself — loops
                    // are driven by edge redelivery
                    ownWrites.put(from, entry.getValue());
                    for (var edge : assembly.outEdges().getOrDefault(from, List.of())) {
                        bind(bindings, pinVersion, edge.to(), entry.getValue());
                    }
                }
                enqueueReady(spec, assembly, bindings, pinVersion, firedVersion, fired, queue,
                        queued);
            }
            var neverFired = spec.nodes().stream()
                    .map(NodeSpec::id)
                    .filter(id -> !fired.contains(id))
                    .toList();
            var payload = new LinkedHashMap<String, Object>();
            payload.put("status", "ok");
            payload.put("firings", firings);
            payload.put("neverFired", neverFired);
            emit(EventKinds.RUN_END, runId, sessionId, null, Map.copyOf(payload));
            var result = new LinkedHashMap<>(bindings);
            result.putAll(ownWrites);
            return Map.copyOf(result);
        } catch (PipelineException e) {
            emit(EventKinds.RUN_END, runId, sessionId, null,
                    Map.of("status", "error", "error", String.valueOf(e.getMessage())));
            throw e;
        }
    }

    private Assembly assemble(PipelineSpec spec) {
        var nodesById = new LinkedHashMap<String, NodeSpec>();
        var nodeImpls = new HashMap<String, Node>();
        var readsByNode = new HashMap<String, List<String>>();
        var outEdges = new HashMap<PinRef, List<EdgeSpec>>();
        for (var edge : spec.edges()) {
            outEdges.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
        }
        try {
            for (var nodeSpec : spec.nodes()) {
                nodesById.put(nodeSpec.id(), nodeSpec);
                var node = services.require(Node.class, nodeSpec.type());
                nodeImpls.put(nodeSpec.id(), node);
                readsByNode.put(nodeSpec.id(),
                        node.contract(nodeSpec.config()).reads().stream()
                                .map(NodeContract.Key::name).toList());
            }
        } catch (Exception e) {
            throw new PipelineException(
                    "cannot assemble graph '" + spec.name() + "': " + e.getMessage(), e);
        }
        return new Assembly(nodesById, nodeImpls, readsByNode, outEdges);
    }

    /**
     * Binds a value delivered to a pin (edge delivery or run input).
     * Bindings are sticky (a later write overwrites the earlier one) and
     * re-delivering an equal value does not count as new, so idempotent
     * writes never re-trigger downstream nodes.
     *
     * @return true if the pin received a new value
     */
    private static boolean bind(Map<PinRef, Object> bindings, Map<PinRef, Integer> pinVersion,
                                PinRef pin, Object value) {
        var old = bindings.get(pin);
        if (old != null && old.equals(value)) {
            return false;
        }
        bindings.put(pin, value);
        pinVersion.merge(pin, 1, Integer::sum);
        return true;
    }

    private static void enqueueReady(PipelineSpec spec, Assembly assembly,
                                     Map<PinRef, Object> bindings, Map<PinRef, Integer> pinVersion,
                                     Map<String, Integer> firedVersion, Set<String> fired,
                                     ArrayDeque<String> queue, Set<String> queued) {
        for (var node : spec.nodes()) {
            var id = node.id();
            if (!queued.contains(id) && isReady(id, assembly.readsByNode().get(id), pinVersion,
                    firedVersion, fired)) {
                queue.add(id);
                queued.add(id);
            }
        }
    }

    /**
     * Ready when every read carries a binding and the newest binding among
     * them is newer than the last firing; the strict rule the outer graph and
     * every loop body share.
     */
    private static boolean isReady(String id, List<String> reads,
                                   Map<PinRef, Integer> pinVersion,
                                   Map<String, Integer> firedVersion, Set<String> fired) {
        if (reads.isEmpty()) {
            return !fired.contains(id);
        }
        var newest = readVersion(id, reads, pinVersion);
        return newest > 0 && newest > firedVersion.getOrDefault(id, 0);
    }

    /** The newest binding version across a node's read pins; 0 if any is unbound. */
    private static int readVersion(String id, List<String> reads,
                                   Map<PinRef, Integer> pinVersion) {
        var newest = 0;
        for (var key : reads) {
            var version = pinVersion.getOrDefault(new PinRef(id, key), 0);
            if (version == 0) {
                return 0;
            }
            newest = Math.max(newest, version);
        }
        return newest;
    }

    private static Map<String, Object> triggerInput(String id, List<String> reads,
                                                    Map<PinRef, Object> bindings) {
        var snapshot = new LinkedHashMap<String, Object>();
        for (var key : reads) {
            snapshot.put(key, bindings.get(new PinRef(id, key)));
        }
        return Map.copyOf(snapshot);
    }

    private record Assembly(Map<String, NodeSpec> nodesById, Map<String, Node> nodeImpls,
                            Map<String, List<String>> readsByNode,
                            Map<PinRef, List<EdgeSpec>> outEdges) {
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
