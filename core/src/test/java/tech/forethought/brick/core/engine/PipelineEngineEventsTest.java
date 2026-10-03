package tech.forethought.brick.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.event.EventListener;
import tech.forethought.brick.core.event.TraceEvent;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;
import tech.forethought.brick.core.testkit.ManualServices;

class PipelineEngineEventsTest {

    private static final class Recorder implements EventListener {
        private final List<TraceEvent> events = new ArrayList<>();

        @Override
        public void onEvent(TraceEvent event) {
            events.add(event);
        }
    }

    /** Test node: reads "seed", writes "x" = 1. */
    static final class SeedNode implements Node {
        @Override
        public String type() {
            return "seed";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(
                    List.of(new Key("seed", ValueType.ANY)),
                    List.of(new Key("x", ValueType.NUMBER)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            return Map.of("x", 1);
        }
    }

    private static ManualServices services() {
        return new ManualServices()
                .with(Node.class, "put", new PipelineEngineTest.PutNode())
                .with(Node.class, "fail", new PipelineEngineTest.FailNode())
                .with(Node.class, "seed", new SeedNode());
    }

    private static PipelineSpec spec() {
        return new PipelineSpec("demo",
                List.of(new NodeSpec("a", "put", Map.of("key", "x", "value", 1))),
                List.of(), List.of(), List.of());
    }

    private static PipelineEngine engine(Recorder recorder, boolean debug) {
        return new PipelineEngine(services(), new EngineConfig(List.of(recorder), debug,
                Set.of()));
    }

    @Test
    void emitsRunAndNodeEventsInOrder() {
        var recorder = new Recorder();
        engine(recorder, false).run(spec(), Map.of(), "s1");
        var kinds = recorder.events.stream().map(TraceEvent::kind).toList();
        assertEquals(List.of(EventKinds.RUN_START, EventKinds.NODE_ENTER,
                EventKinds.NODE_EXIT, EventKinds.RUN_END), kinds);
        assertTrue(recorder.events.stream().allMatch(e -> "s1".equals(e.sessionId())));
        assertEquals("a", recorder.events.get(1).nodeId());
    }

    @Test
    void nodePayloadIsTheTriggerBindingSnapshot() {
        var recorder = new Recorder();
        var spec = new PipelineSpec("seeded",
                List.of(new NodeSpec("a", "seed", Map.of())), List.of(),
                List.of(new PinRef("a", "seed")), List.of());
        engine(recorder, false).run(spec, Map.of(new PinRef("a", "seed"), 5));
        var enter = recorder.events.get(1);
        assertEquals(EventKinds.NODE_ENTER, enter.kind());
        assertEquals("Integer", enter.payload().get("seed"));
        var exit = recorder.events.get(2);
        assertEquals(EventKinds.NODE_EXIT, exit.kind());
        assertEquals("Integer", exit.payload().get("x"));
    }

    @Test
    void summariesByDefault() {
        var recorder = new Recorder();
        engine(recorder, false).run(spec(), Map.of());
        var enter = recorder.events.get(1);
        assertEquals(EventKinds.NODE_ENTER, enter.kind());
        assertEquals(Map.of(), enter.payload());
    }

    @Test
    void debugSnapshotsCarryRedactedData() {
        var recorder = new Recorder();
        var spec = new PipelineSpec("seeded",
                List.of(new NodeSpec("a", "seed", Map.of())), List.of(),
                List.of(new PinRef("a", "seed")), List.of());
        engine(recorder, true).run(spec,
                Map.of(new PinRef("a", "seed"), Map.of("authToken", "secret-value")));
        var enter = recorder.events.get(1);
        @SuppressWarnings("unchecked")
        var seed = (Map<String, Object>) enter.payload().get("seed");
        assertEquals("***", seed.get("authToken"));
    }

    @Test
    void runEndReportsFiringsAndNeverFiredNodes() {
        var recorder = new Recorder();
        var spec = new PipelineSpec("with-dormant",
                List.of(new NodeSpec("a", "put", Map.of("key", "x", "value", 1)),
                        new NodeSpec("dormant", "seed", Map.of())),
                List.of(), List.of(), List.of());
        engine(recorder, false).run(spec, Map.of());
        var end = recorder.events.getLast();
        assertEquals(EventKinds.RUN_END, end.kind());
        assertEquals("ok", end.payload().get("status"));
        assertEquals(1, end.payload().get("firings"));
        assertEquals(List.of("dormant"), end.payload().get("neverFired"));
    }

    @Test
    void failingListenerNeverAffectsRun() {
        var recorder = new Recorder();
        EventListener bad = event -> {
            throw new RuntimeException("listener boom");
        };
        var engine = new PipelineEngine(services(),
                new EngineConfig(List.of(bad, recorder), false, Set.of()));
        var result = engine.run(spec(), Map.of());
        assertEquals(1, result.get(new PinRef("a", "x")));
        assertEquals(4, recorder.events.size());
    }

    @Test
    void runFailureEmitsErrorRunEnd() {
        var recorder = new Recorder();
        var failing = new PipelineSpec("failing",
                List.of(new NodeSpec("bad", "fail", Map.of())), List.of(), List.of(), List.of());
        try {
            engine(recorder, false).run(failing, Map.of());
        } catch (PipelineException expected) {
            // expected
        }
        var last = recorder.events.getLast();
        assertEquals(EventKinds.RUN_END, last.kind());
        assertEquals("error", last.payload().get("status"));
    }
}
