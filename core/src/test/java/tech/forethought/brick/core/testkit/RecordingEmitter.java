package tech.forethought.brick.core.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.event.EventEmitter;

/** An emitter that records everything for assertions. Not thread-safe. */
public final class RecordingEmitter implements EventEmitter {

    /** One recorded emission. */
    public record Emitted(String kind, Map<String, Object> payload) {
    }

    private final List<Emitted> events = new ArrayList<>();

    @Override
    public void emit(String kind, Map<String, Object> payload) {
        events.add(new Emitted(kind, Map.copyOf(payload)));
    }

    /** The recorded events in emission order. */
    public List<Emitted> events() {
        return List.copyOf(events);
    }
}
