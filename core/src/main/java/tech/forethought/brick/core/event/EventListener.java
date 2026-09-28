package tech.forethought.brick.core.event;

/**
 * A passive observer of the event stream (observability options pattern:
 * events are broadcast to all selected implementations). Implementations
 * must be thread-safe and must not throw — a failing listener never affects
 * the run or other listeners.
 */
public interface EventListener {

    /** Called once per event, in emission order. */
    void onEvent(TraceEvent event);
}
