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
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.testkit.ManualServices;

class PipelineEngineEventsTest {

    private static final class Recorder implements EventListener {
        private final List<TraceEvent> events = new ArrayList<>();

        @Override
        public void onEvent(TraceEvent event) {
            events.add(event);
        }
    }

    private static ManualServices services() {
        return new ManualServices()
                .with(Node.class, "put", new PipelineEngineTest.PutNode())
                .with(Node.class, "fail", new PipelineEngineTest.FailNode());
    }

    private static PipelineSpec spec() {
        return new PipelineSpec("demo",
                List.of(new NodeSpec("a", "put", Map.of("key", "x", "value", 1))),
                List.of(), "a");
    }

    private static PipelineEngine engine(Recorder recorder, boolean debug) {
        return new PipelineEngine(services(), new EngineConfig(List.of(recorder), debug,
                Set.of()));
    }

    @Test
    void emitsRunAndNodeEventsInOrder() {
        var recorder = new Recorder();
        engine(recorder, false).run(spec(), Map.of(EdgeKeys.SESSION_ID, "s1"));
        var kinds = recorder.events.stream().map(TraceEvent::kind).toList();
        assertEquals(List.of(EventKinds.RUN_START, EventKinds.NODE_ENTER,
                EventKinds.NODE_EXIT, EventKinds.RUN_END), kinds);
        assertTrue(recorder.events.stream().allMatch(e -> "s1".equals(e.sessionId())));
        assertEquals("a", recorder.events.get(1).nodeId());
    }

    @Test
    void summariesByDefault() {
        var recorder = new Recorder();
        engine(recorder, false).run(spec(), Map.of("seed", 5));
        var enter = recorder.events.get(1);
        assertEquals(EventKinds.NODE_ENTER, enter.kind());
        assertEquals("Integer", enter.payload().get("seed"));
    }

    @Test
    void debugSnapshotsCarryRedactedData() {
        var recorder = new Recorder();
        engine(recorder, true).run(spec(), Map.of("authToken", "secret-value", "seed", 5));
        var enter = recorder.events.get(1);
        assertEquals("***", enter.payload().get("authToken"));
        assertEquals(5, enter.payload().get("seed"));
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
        assertEquals(1, result.get("x"));
        assertEquals(4, recorder.events.size());
    }

    @Test
    void runFailureEmitsErrorRunEnd() {
        var recorder = new Recorder();
        var failing = new PipelineSpec("failing",
                List.of(new NodeSpec("bad", "fail", Map.of())), List.of(), "bad");
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
