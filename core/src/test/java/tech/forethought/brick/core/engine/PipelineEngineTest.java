package tech.forethought.brick.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;
import tech.forethought.brick.core.testkit.ManualServices;

class PipelineEngineTest {

    /** Test node: zero reads, writes config "key" = "value". */
    static final class PutNode implements Node {
        @Override
        public String type() {
            return "put";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            // the written key's name comes from config "key"
            var key = config.get("key");
            return key == null ? NodeContract.empty()
                    : new NodeContract(List.of(),
                            List.of(new Key(String.valueOf(key), ValueType.ANY)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var out = new LinkedHashMap<String, Object>();
            out.put(String.valueOf(context.config().get("key")), context.config().get("value"));
            return out;
        }
    }

    /** Test node: passes its "in" read through to "out". */
    static final class ForwardNode implements Node {
        @Override
        public String type() {
            return "forward";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key("in", ValueType.ANY)),
                    List.of(new Key("out", ValueType.ANY)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var out = new LinkedHashMap<String, Object>();
            out.put("out", input.get("in"));
            return out;
        }
    }

    /** Test node: selective delivery — writes "true" or "false" carrying "value". */
    static final class GateNode implements Node {
        @Override
        public String type() {
            return "gate";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(
                    List.of(new Key("control", ValueType.ANY), new Key("value", ValueType.ANY)),
                    List.of(new Key("true", ValueType.ANY), new Key("false", ValueType.ANY)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var out = new LinkedHashMap<String, Object>();
            out.put(Boolean.TRUE.equals(input.get("control")) ? "true" : "false",
                    input.get("value"));
            return out;
        }
    }

    /** Test node: counts up on "n"; delivers "again" only while below config "until". */
    static final class CountNode implements Node {
        @Override
        public String type() {
            return "count";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key("n", ValueType.NUMBER)),
                    List.of(new Key("n", ValueType.NUMBER), new Key("again", ValueType.NUMBER)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var n = ((Number) input.get("n")).intValue() + 1;
            var until = ((Number) context.config().get("until")).intValue();
            var out = new LinkedHashMap<String, Object>();
            out.put("n", n);
            if (n < until) {
                out.put("again", n);
            }
            return out;
        }
    }

    /** Test node: always fails. */
    static final class FailNode implements Node {
        @Override
        public String type() {
            return "fail";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return NodeContract.empty();
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            throw new IllegalStateException("boom");
        }
    }

    /** Test node: tries to mutate its input. */
    static final class MutatorNode implements Node {
        @Override
        public String type() {
            return "mutator";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key("in", ValueType.ANY)), List.of());
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            input.put("hacked", true);
            return Map.of();
        }
    }

    private static ManualServices services() {
        return new ManualServices()
                .with(Node.class, "put", new PutNode())
                .with(Node.class, "forward", new ForwardNode())
                .with(Node.class, "gate", new GateNode())
                .with(Node.class, "count", new CountNode())
                .with(Node.class, "fail", new FailNode())
                .with(Node.class, "mutator", new MutatorNode());
    }

    private static NodeSpec node(String id, String type, Map<String, Object> config) {
        return new NodeSpec(id, type, config);
    }

    private static EdgeSpec edge(String fromNode, String fromKey, String toNode, String toKey) {
        return new EdgeSpec(new PinRef(fromNode, fromKey), new PinRef(toNode, toKey));
    }

    @Test
    void runsLinearChain() {
        var spec = new PipelineSpec("linear",
                List.of(node("a", "put", Map.of("key", "x", "value", 1)),
                        node("b", "forward", Map.of())),
                List.of(edge("a", "x", "b", "in")),
                List.of(), List.of());
        var result = new PipelineEngine(services()).run(spec, Map.of());
        assertEquals(1, result.get(new PinRef("b", "out")));
    }

    @Test
    void runInputIsInjectedIntoDeclaredInputPins() {
        var spec = new PipelineSpec("inject",
                List.of(node("b", "forward", Map.of())),
                List.of(),
                List.of(new PinRef("b", "in")), List.of());
        var result = new PipelineEngine(services()).run(spec,
                Map.of(new PinRef("b", "in"), "hello"));
        assertEquals("hello", result.get(new PinRef("b", "out")));
    }

    @Test
    void terminalNodeEndsTheRun() {
        var spec = new PipelineSpec("single",
                List.of(node("only", "put", Map.of("key", "x", "value", 1))),
                List.of(),
                List.of(), List.of());
        var result = new PipelineEngine(services()).run(spec, Map.of());
        assertEquals(1, result.get(new PinRef("only", "x")));
    }

    @Test
    void deliversSelectivelyDownTheTakenBranchOnly() {
        var spec = new PipelineSpec("branch",
                List.of(node("gate", "gate", Map.of()),
                        node("left", "forward", Map.of()),
                        node("right", "forward", Map.of())),
                List.of(edge("gate", "true", "left", "in"),
                        edge("gate", "false", "right", "in")),
                List.of(), List.of());
        var result = new PipelineEngine(services()).run(spec, Map.of(
                new PinRef("gate", "control"), true,
                new PinRef("gate", "value"), "L"));
        assertEquals("L", result.get(new PinRef("left", "out")));
        assertFalse(result.containsKey(new PinRef("right", "out")));
    }

    @Test
    void unselectedBranchNodeNeverFires() {
        var spec = new PipelineSpec("half-dead",
                List.of(node("gate", "gate", Map.of()),
                        node("left", "forward", Map.of()),
                        node("right", "forward", Map.of())),
                List.of(edge("gate", "true", "left", "in"),
                        edge("gate", "false", "right", "in")),
                List.of(), List.of());
        var result = new PipelineEngine(services()).run(spec, Map.of(
                new PinRef("gate", "control"), true,
                new PinRef("gate", "value"), "L"));
        assertEquals("L", result.get(new PinRef("left", "out")));
        assertFalse(result.containsKey(new PinRef("right", "out")));
    }

    @Test
    void followsBackEdgeUntilDeliveryStops() {
        var spec = new PipelineSpec("loop",
                List.of(node("count", "count", Map.of("until", 3)),
                        node("end", "forward", Map.of())),
                List.of(edge("count", "again", "count", "n"),
                        edge("count", "n", "end", "in")),
                List.of(new PinRef("count", "n")), List.of());
        var result = new PipelineEngine(services()).run(spec,
                Map.of(new PinRef("count", "n"), 0));
        assertEquals(3, result.get(new PinRef("count", "n")));
        assertEquals(3, result.get(new PinRef("end", "out")));
    }

    @Test
    void multipleInEdgesOverwriteAndRefire() {
        var spec = new PipelineSpec("join",
                List.of(node("a", "put", Map.of("key", "x", "value", 1)),
                        node("b", "put", Map.of("key", "x", "value", 2)),
                        node("c", "forward", Map.of())),
                List.of(edge("a", "x", "c", "in"), edge("b", "x", "c", "in")),
                List.of(), List.of());
        var result = new PipelineEngine(services()).run(spec, Map.of());
        assertEquals(2, result.get(new PinRef("c", "out")));
    }

    @Test
    void loopProtectionKillsEndlessRuns() {
        var spec = new PipelineSpec("endless",
                List.of(node("count", "count", Map.of("until", 100))),
                List.of(edge("count", "again", "count", "n")),
                List.of(new PinRef("count", "n")), List.of());
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of(new PinRef("count", "n"), 0)));
        assertTrue(e.getMessage().contains("loop protection"));
        assertTrue(e.getMessage().contains("maxFirings"));
    }

    @Test
    void nodeFailureCarriesNodeIdentity() {
        var spec = new PipelineSpec("failing",
                List.of(node("bad", "fail", Map.of())),
                List.of(),
                List.of(), List.of());
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
        assertTrue(e.getMessage().contains("bad"));
        assertTrue(e.getMessage().contains("boom"));
    }

    @Test
    void unknownNodeTypeFailsWithPreciseMessage() {
        var spec = new PipelineSpec("unknown",
                List.of(node("mystery", "no-such-type", Map.of())),
                List.of(),
                List.of(), List.of());
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
        assertTrue(e.getMessage().contains("no-such-type"));
    }

    @Test
    void inputSnapshotIsImmutableForNodes() {
        var spec = new PipelineSpec("immutability",
                List.of(node("evil", "mutator", Map.of())),
                List.of(),
                List.of(new PinRef("evil", "in")), List.of());
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of(new PinRef("evil", "in"), 1)));
        assertTrue(e.getMessage().contains("evil"));
    }

    @Test
    void specErrorsAreReportedBeforeRunning() {
        var spec = new PipelineSpec("broken",
                List.of(node("a", "put", Map.of("key", "x", "value", 1))),
                List.of(edge("a", "x", "ghost", "in")),
                List.of(), List.of());
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
        assertTrue(e.getMessage().contains("ghost"));
    }
}
