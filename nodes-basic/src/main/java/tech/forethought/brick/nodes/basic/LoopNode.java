package tech.forethought.brick.nodes.basic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tech.forethought.brick.core.engine.EngineConfig;
import tech.forethought.brick.core.engine.PipelineEngine;
import tech.forethought.brick.core.engine.PipelineException;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.event.EventListener;
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spec.PipelineSpecCodec;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;
import tech.forethought.brick.core.spi.Services;

/**
 * Loop container: repetition leaves the graph and lives inside this node, so
 * the outer graph (and every loop body) stays a plain DAG ruled by the
 * strictest trigger semantics. Config: {@code body} — a complete pipeline
 * document (map form, parsed by {@link PipelineSpecCodec}); {@code
 * condition} — {@code {"pin": <body output key>, "initial": <value>}}; and
 * {@code maxIterations} (default {@value #DEFAULT_MAX_ITERATIONS}).
 *
 * <p>Base semantics are while (condition first): read the condition's
 * carried value (the {@code initial} on the first round, the body's output
 * of the same name afterwards); when it is not {@code Boolean.TRUE} the loop
 * ends and the carried values' finals are written to the container's output
 * pins. A truthy condition runs the body once through a nested
 * {@link PipelineEngine}, injecting the body-declared inputs; the body's
 * declared outputs then update the carried values. Carried values are the
 * body inputs refreshed by same-named outputs (the condition pin among
 * them, recomputed every round); an output declaration may carry an
 * {@code "as"} field to expose itself under a different container pin name
 * when the body's producer pin is named differently. Inputs the body never
 * re-produces (configuration) simply persist their injected value across
 * rounds. When the body never runs, output pins take the carried values'
 * initial values; a carried output with neither initial nor injection is
 * omitted. Hitting the iteration cap throws {@link PipelineException} (loop
 * protection). When the condition pin is not among the body's outputs the
 * body can never flip it: with a truthy initial the loop certainly spins
 * to the cap, otherwise it certainly never runs — either way a warning
 * event is emitted and the run continues.
 *
 * <p>The nested runs re-announce every event through {@code
 * context.events()}, so observation, persistence and streaming stay
 * unbroken; the nested run's session carries the parent run id. Thread-safe
 * (stateless): the body is parsed per call.
 */
public final class LoopNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "loop";

    /** Default cap on body evaluations per execution: loop protection. */
    public static final int DEFAULT_MAX_ITERATIONS = 50;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract(Map<String, Object> config) {
        try {
            var parsed = Parsed.parse(config);
            return new NodeContract(keysToAny(parsed.inputKeys()),
                    keysToAny(parsed.exposedKeys()));
        } catch (RuntimeException e) {
            // a broken body reports precisely at execution time
            return NodeContract.empty();
        }
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var parsed = Parsed.parse(context.config());
        if (!parsed.exposedKeys().contains(parsed.conditionPin())) {
            warnUnproducedCondition(context, parsed);
        }
        var carried = new LinkedHashMap<String, Object>();
        for (var key : parsed.inputKeys()) {
            if (input.containsKey(key)) {
                carried.put(key, input.get(key));
            }
        }
        if (parsed.hasInitial()) {
            carried.put(parsed.conditionPin(), parsed.initial());
        }

        var iterations = 0;
        var engine = nestedEngine(context);
        while (Boolean.TRUE.equals(carried.get(parsed.conditionPin()))) {
            if (++iterations > parsed.maxIterations()) {
                throw new PipelineException(
                        "loop protection: exceeded maxIterations=" + parsed.maxIterations());
            }
            var inject = new LinkedHashMap<PinRef, Object>();
            for (var pin : parsed.body().inputs()) {
                var value = carried.get(pin.key());
                if (value == null && !carried.containsKey(pin.key())) {
                    throw new IllegalArgumentException(
                            "loop node: body input '" + pin.key() + "' has no value to inject");
                }
                inject.put(pin, value);
            }
            var result = engine.run(parsed.body(), Map.copyOf(inject), context.runId());
            for (var mapping : parsed.outputs()) {
                var value = result.get(new PinRef(mapping.node(), mapping.key()));
                if (value != null) {
                    carried.put(mapping.exposed(), value);
                }
            }
        }

        var out = new LinkedHashMap<String, Object>();
        for (var key : parsed.exposedKeys()) {
            var value = carried.get(key);
            if (value != null) {
                out.put(key, value);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * A static tautology: the body can never flip the condition, so the loop
     * either spins to the iteration cap or never runs. Warn on the event
     * stream; the run continues (diagnostics, not gatekeepers).
     */
    private static void warnUnproducedCondition(NodeContext context, Parsed parsed) {
        var runsUntilCap = Boolean.TRUE.equals(parsed.initial());
        var detail = runsUntilCap
                ? "the loop runs until maxIterations"
                : "the body never runs";
        context.events().emit(EventKinds.WARNING, Map.of("message",
                "loop node: condition pin '" + parsed.conditionPin()
                        + "' is not produced by any body output; " + detail));
    }

    private static List<Key> keysToAny(List<String> keys) {
        var result = new ArrayList<Key>();
        for (var key : keys) {
            result.add(new Key(key, ValueType.ANY));
        }
        return result;
    }

    private static PipelineEngine nestedEngine(NodeContext context) {
        EventListener forwarder = event -> context.events().emit(event.kind(), event.payload());
        // the nested engine must not also notify service-registered
        // listeners, or every inner event would be delivered twice
        var hidden = new EventListenerFreeServices(context.services());
        var config = new EngineConfig(List.of(forwarder), false, Set.of());
        return new PipelineEngine(hidden, config);
    }

    /** One body output declaration: read at a body pin, exposed under a name. */
    private record OutputMapping(String node, String key, String exposed) {
    }

    private record Parsed(PipelineSpec body, List<String> inputKeys, String conditionPin,
                          Object initial, boolean hasInitial, int maxIterations,
                          List<OutputMapping> outputs, List<String> exposedKeys) {

        static Parsed parse(Map<String, Object> config) {
            var body = parseBody(config);
            var inputKeys = new ArrayList<String>();
            for (var pin : body.inputs()) {
                if (!inputKeys.contains(pin.key())) {
                    inputKeys.add(pin.key());
                }
            }
            var condition = config.get("condition");
            if (!(condition instanceof Map<?, ?> conditionMap)) {
                throw new IllegalArgumentException("loop node requires config 'condition'");
            }
            var pin = conditionMap.get("pin");
            if (!(pin instanceof String conditionPin)) {
                throw new IllegalArgumentException(
                        "loop node: config 'condition' requires a string 'pin'");
            }
            var maxIterations = DEFAULT_MAX_ITERATIONS;
            if (config.get("maxIterations") != null) {
                if (!(config.get("maxIterations") instanceof Number number)) {
                    throw new IllegalArgumentException(
                            "loop node: config 'maxIterations' must be a number");
                }
                maxIterations = number.intValue();
            }
            var outputs = parseOutputs(requireRawBody(config));
            var exposedKeys = new ArrayList<String>();
            for (var mapping : outputs) {
                if (!exposedKeys.contains(mapping.exposed())) {
                    exposedKeys.add(mapping.exposed());
                }
            }
            return new Parsed(body, List.copyOf(inputKeys), conditionPin,
                    conditionMap.get("initial"), conditionMap.containsKey("initial"),
                    maxIterations, outputs, List.copyOf(exposedKeys));
        }

        @SuppressWarnings("unchecked")
        private static PipelineSpec parseBody(Map<String, Object> config) {
            var body = config.get("body");
            if (body == null) {
                throw new IllegalArgumentException("loop node requires config 'body'");
            }
            if (!(body instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("loop node: config 'body' must be an object");
            }
            return PipelineSpecCodec.fromMap((Map<String, Object>) body);
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> requireRawBody(Map<String, Object> config) {
            return (Map<String, Object>) config.get("body");
        }

        private static List<OutputMapping> parseOutputs(Map<String, Object> rawBody) {
            var mappings = new ArrayList<OutputMapping>();
            var outputs = rawBody.get("outputs");
            if (outputs == null) {
                return List.of();
            }
            if (!(outputs instanceof List<?> list)) {
                throw new IllegalArgumentException(
                        "loop node: config 'body' field 'outputs' must be an array");
            }
            for (var element : list) {
                if (!(element instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException(
                            "loop node: config 'body' outputs must be objects");
                }
                var node = map.get("node");
                var key = map.get("key");
                if (!(node instanceof String nodeId) || !(key instanceof String keyName)) {
                    throw new IllegalArgumentException(
                            "loop node: config 'body' outputs require string 'node' and 'key'");
                }
                var exposed = map.get("as");
                mappings.add(new OutputMapping(nodeId, keyName,
                        exposed instanceof String alias ? alias : keyName));
            }
            return List.copyOf(mappings);
        }
    }

    /**
     * Delegating {@link Services} that hides event listeners: the nested
     * engine forwards inner events through the container's emitter instead,
     * so service-registered listeners must not see them a second time.
     * Thread-safe (stateless): delegates to the wrapped services.
     */
    private record EventListenerFreeServices(Services delegate) implements Services {

        @Override
        public <T> T require(Class<T> spiType, String name) {
            return delegate.require(spiType, name);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> all(Class<T> spiType) {
            if (spiType == EventListener.class) {
                return List.of();
            }
            return delegate.all(spiType);
        }
    }
}
