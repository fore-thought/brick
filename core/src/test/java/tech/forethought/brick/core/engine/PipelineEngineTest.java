package tech.forethought.brick.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.testkit.ManualServices;

class PipelineEngineTest {

    /** Test node: puts config "key" = "value" into the data. */
    static final class PutNode implements Node {
        @Override
        public String type() {
            return "put";
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var out = new LinkedHashMap<>(input);
            out.put(String.valueOf(context.config().get("key")), context.config().get("value"));
            return out;
        }
    }

    /** Test node: routes by the string form of the input value at config "key". */
    static final class RouteByNode implements Node {
        @Override
        public String type() {
            return "route-by";
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var out = new LinkedHashMap<>(input);
            out.put(EdgeKeys.ROUTE,
                    String.valueOf(input.get(String.valueOf(context.config().get("key")))));
            return out;
        }
    }

    /** Test node: increments "n" and routes "again" until it reaches config "until". */
    static final class CountNode implements Node {
        @Override
        public String type() {
            return "count";
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var n = ((Number) input.getOrDefault("n", 0)).intValue() + 1;
            var until = ((Number) context.config().get("until")).intValue();
            var out = new LinkedHashMap<>(input);
            out.put("n", n);
            out.put(EdgeKeys.ROUTE, n < until ? "again" : "done");
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
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            input.put("hacked", true);
            return input;
        }
    }

    private static ManualServices services() {
        return new ManualServices()
                .with(Node.class, "put", new PutNode())
                .with(Node.class, "route-by", new RouteByNode())
                .with(Node.class, "count", new CountNode())
                .with(Node.class, "fail", new FailNode())
                .with(Node.class, "mutator", new MutatorNode());
    }

    private static NodeSpec node(String id, String type, Map<String, Object> config) {
        return new NodeSpec(id, type, config);
    }

    @Test
    void runsLinearChain() {
        var spec = new PipelineSpec("linear",
                List.of(node("a", "put", Map.of("key", "x", "value", 1)),
                        node("b", "put", Map.of("key", "y", "value", 2))),
                List.of(new EdgeSpec("a", "b", null)),
                "a");
        var result = new PipelineEngine(services()).run(spec, Map.of());
        assertEquals(1, result.get("x"));
        assertEquals(2, result.get("y"));
    }

    @Test
    void terminalNodeEndsTheRun() {
        var spec = new PipelineSpec("single",
                List.of(node("only", "put", Map.of("key", "x", "value", 1))),
                List.of(),
                "only");
        var result = new PipelineEngine(services()).run(spec, Map.of("seed", true));
        assertEquals(true, result.get("seed"));
        assertEquals(1, result.get("x"));
    }

    @Test
    void routesByLabelAndStripsRouteKey() {
        var spec = new PipelineSpec("branch",
                List.of(node("start", "put", Map.of("key", "flag", "value", "left")),
                        node("route", "route-by", Map.of("key", "flag")),
                        node("left", "put", Map.of("key", "side", "value", "L")),
                        node("right", "put", Map.of("key", "side", "value", "R"))),
                List.of(new EdgeSpec("start", "route", null),
                        new EdgeSpec("route", "left", "left"),
                        new EdgeSpec("route", "right", "right")),
                "start");
        var result = new PipelineEngine(services()).run(spec, Map.of());
        assertEquals("L", result.get("side"));
        assertFalse(result.containsKey(EdgeKeys.ROUTE));
    }

    @Test
    void followsBackEdgeUntilRouteChanges() {
        var spec = new PipelineSpec("loop",
                List.of(node("count", "count", Map.of("until", 3)),
                        node("end", "put", Map.of("key", "finished", "value", true))),
                List.of(new EdgeSpec("count", "count", "again"),
                        new EdgeSpec("count", "end", "done")),
                "count");
        var result = new PipelineEngine(services()).run(spec, Map.of());
        assertEquals(3, result.get("n"));
        assertEquals(true, result.get("finished"));
    }

    @Test
    void loopProtectionKillsEndlessRuns() {
        var spec = new PipelineSpec("endless",
                List.of(node("count", "count", Map.of("until", 100))),
                List.of(new EdgeSpec("count", "count", "again"),
                        new EdgeSpec("count", "count", "done")),
                "count");
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
        assertTrue(e.getMessage().contains("loop protection"));
    }

    @Test
    void nodeFailureCarriesNodeIdentity() {
        var spec = new PipelineSpec("failing",
                List.of(node("bad", "fail", Map.of())),
                List.of(),
                "bad");
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
                "mystery");
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
        assertTrue(e.getMessage().contains("no-such-type"));
    }

    @Test
    void unroutableTransitionFails() {
        var spec = new PipelineSpec("unroutable",
                List.of(node("route", "route-by", Map.of("key", "missing")),
                        node("left", "put", Map.of("key", "x", "value", 1))),
                List.of(new EdgeSpec("route", "left", "left")),
                "route");
        assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
    }

    @Test
    void inputSnapshotIsImmutableForNodes() {
        var spec = new PipelineSpec("immutability",
                List.of(node("evil", "mutator", Map.of())),
                List.of(),
                "evil");
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
        assertTrue(e.getMessage().contains("evil"));
    }

    @Test
    void specErrorsAreReportedBeforeRunning() {
        var spec = new PipelineSpec("broken",
                List.of(node("a", "put", Map.of("key", "x", "value", 1))),
                List.of(new EdgeSpec("a", "ghost", null)),
                "a");
        var e = assertThrows(PipelineException.class,
                () -> new PipelineEngine(services()).run(spec, Map.of()));
        assertTrue(e.getMessage().contains("ghost"));
    }
}
