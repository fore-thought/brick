package tech.forethought.brick.core.event;

import java.util.Map;

/**
 * Handle handed to nodes for emitting events onto the run's event stream.
 * Thread-safe: events are stamped and broadcast by the engine.
 */
public interface EventEmitter {

    /** Emits an event of the given kind with the given payload. */
    void emit(String kind, Map<String, Object> payload);

    /** An emitter that drops everything (for tests and minimal contexts). */
    static EventEmitter noop() {
        return (kind, payload) -> {
        };
    }
}
