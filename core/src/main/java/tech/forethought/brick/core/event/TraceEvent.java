package tech.forethought.brick.core.event;

import java.util.Map;

/**
 * One fact on the run's event stream. The stream is append-only;
 * observation, persistence, and live display are all listeners.
 *
 * @param kind      event kind (see {@link EventKinds}; extensions may add
 *                  their own)
 * @param timestamp epoch millis
 * @param runId     the run this event belongs to
 * @param sessionId the session this run belongs to
 * @param nodeId    the emitting node, null for engine-level events
 * @param payload   event data (sensitive keys redacted by the engine)
 */
public record TraceEvent(String kind, long timestamp, String runId, String sessionId,
                         String nodeId, Map<String, Object> payload) {

    public TraceEvent {
        payload = Map.copyOf(payload);
    }
}
