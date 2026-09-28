package tech.forethought.brick.core.testkit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventListener;
import tech.forethought.brick.core.event.TraceEvent;

/**
 * Reusable contract suite for {@link EventListener}: a listener must accept
 * any event without throwing. Subclass and provide the listener under test.
 */
public abstract class EventListenerContractTest {

    protected abstract EventListener subject();

    @Test
    void acceptsEventsWithoutThrowing() {
        var event = new TraceEvent("run-start", 0L, "run", "session", null, Map.of());
        assertDoesNotThrow(() -> subject().onEvent(event));
    }
}
