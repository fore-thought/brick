package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.engine.PipelineException;
import tech.forethought.brick.core.event.EventEmitter;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spec.PipelineSpecCodec;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;
import tech.forethought.brick.core.testkit.RecordingEmitter;

public final class LoopNodeTest extends NodeContractTest {

    /** Test body node: increments "n"; "more" stays true while below config "limit". */
    static final class StepNode implements Node {
        @Override
        public String type() {
            return "step";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(
                    List.of(new NodeContract.Key("n", NodeContract.ValueType.NUMBER)),
                    List.of(new NodeContract.Key("n", NodeContract.ValueType.NUMBER),
                            new NodeContract.Key("more", NodeContract.ValueType.BOOLEAN)),
                    false);
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var n = ((Number) input.get("n")).intValue() + 1;
            var limit = ((Number) context.config().get("limit")).intValue();
            var out = new LinkedHashMap<String, Object>();
            out.put("n", n);
            out.put("more", n < limit);
            return out;
        }
    }

    private static ManualServices services() {
        return new ManualServices().with(Node.class, "step", new StepNode());
    }

    @Override
    protected void configureServices(ManualServices services) {
        services.with(Node.class, "step", new StepNode());
    }

    /** A one-node body counting up to config "limit", carried on "n"/"more". */
    private static Map<String, Object> bodyMap(int limit) {
        var body = new PipelineSpec("count-body",
                List.of(new NodeSpec("step", "step", Map.of("limit", limit))),
                List.of(),
                List.of(new PinRef("step", "n")),
                List.of(new PinRef("step", "n"), new PinRef("step", "more")));
        return PipelineSpecCodec.toMap(body);
    }

    private static Map<String, Object> loopConfig(int limit, Object initial, int maxIterations) {
        var config = new LinkedHashMap<String, Object>();
        config.put("body", bodyMap(limit));
        var condition = new LinkedHashMap<String, Object>();
        condition.put("pin", "more");
        condition.put("initial", initial);
        config.put("condition", condition);
        config.put("maxIterations", maxIterations);
        return config;
    }

    /** Aliases the body's "more" output to "running" (the condition pin). */
    private static Map<String, Object> bodyMapWithAlias(int limit) {
        var map = bodyMap(limit);
        @SuppressWarnings("unchecked")
        var outputs = (List<Map<String, Object>>) map.get("outputs");
        outputs.get(1).put("as", "running");
        return map;
    }

    private static Map<String, Object> loopConfigWithAlias(int limit, Object initial,
                                                           int maxIterations) {
        var config = loopConfig(limit, initial, maxIterations);
        config.put("body", bodyMapWithAlias(limit));
        var condition = new LinkedHashMap<String, Object>();
        condition.put("pin", "running");
        condition.put("initial", initial);
        config.put("condition", condition);
        return config;
    }

    @Override
    protected Node subject() {
        return new LoopNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of("n", 0);
    }

    @Override
    protected Map<String, Object> sampleConfig() {
        return loopConfig(3, true, 10);
    }

    @Test
    void runsBodyUntilConditionTurnsFalse() {
        var context = new NodeContext("run", "n", loopConfig(3, true, 10), services(),
                EventEmitter.noop());
        var result = subject().execute(Map.of("n", 0), context);
        assertEquals(Map.of("n", 3, "more", false), result);
    }

    @Test
    void zeroIterationsTakesInitialValues() {
        var context = new NodeContext("run", "n", loopConfig(3, false, 10), services(),
                EventEmitter.noop());
        var result = subject().execute(Map.of("n", 5), context);
        assertEquals(Map.of("n", 5, "more", false), result);
    }

    @Test
    void iterationCapKillsRunawayLoops() {
        var context = new NodeContext("run", "n", loopConfig(1_000, true, 7), services(),
                EventEmitter.noop());
        var e = assertThrows(PipelineException.class,
                () -> subject().execute(Map.of("n", 0), context));
        assertTrue(e.getMessage().contains("loop protection"));
        assertTrue(e.getMessage().contains("maxIterations=7"));
    }

    @Test
    void contractExposesBodyInputAndOutputKeys() {
        var contract = subject().contract(loopConfig(3, true, 10));
        assertEquals(List.of("n"), contract.reads().stream().map(NodeContract.Key::name).toList());
        var writes = contract.writes().stream().map(NodeContract.Key::name).toList();
        assertTrue(writes.contains("n"));
        assertTrue(writes.contains("more"));
        assertTrue(contract.dynamic());
    }

    @Test
    void outputAliasExposesBodyPinUnderAnotherName() {
        var context = new NodeContext("run", "n", loopConfigWithAlias(3, true, 10), services(),
                EventEmitter.noop());
        var result = subject().execute(Map.of("n", 0), context);
        // "more" is exposed as "running"; the carried "n" keeps its name
        assertFalse(result.containsKey("more"));
        assertEquals(3, result.get("n"));
        assertEquals(false, result.get("running"));
    }

    @Test
    void forwardsInnerEventsToTheContainerEmitter() {
        var emitter = new RecordingEmitter();
        var context = new NodeContext("run", "n", loopConfig(2, true, 10), services(), emitter);
        subject().execute(Map.of("n", 0), context);
        var kinds = emitter.events().stream().map(RecordingEmitter.Emitted::kind).toList();
        // two rounds, each a full nested run
        assertEquals(2, kinds.stream().filter(EventKinds.RUN_START::equals).count());
        assertTrue(kinds.contains(EventKinds.NODE_ENTER));
        assertEquals(2, kinds.stream().filter(EventKinds.RUN_END::equals).count());
    }

    @Test
    void missingBodyConfigFailsClearly() {
        var context = new NodeContext("run", "n", Map.of(), services(),
                EventEmitter.noop());
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of("n", 0), context));
        assertEquals("loop node requires config 'body'", e.getMessage());
    }

    @Test
    void unproducedConditionPinFailsClearly() {
        var config = loopConfig(3, true, 10);
        var condition = new LinkedHashMap<String, Object>();
        condition.put("pin", "nope");
        condition.put("initial", true);
        config.put("condition", condition);
        var context = new NodeContext("run", "n", config, services(),
                EventEmitter.noop());
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of("n", 0), context));
        assertEquals("loop node: condition pin 'nope' is not produced by any body output",
                e.getMessage());
    }

    @Test
    void bodyInputWithoutValueFailsClearly() {
        var context = new NodeContext("run", "n", loopConfig(3, true, 10), services(),
                EventEmitter.noop());
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context));
        assertEquals("loop node: body input 'n' has no value to inject", e.getMessage());
    }
}
