package tech.forethought.brick.core.engine;

import java.util.List;
import java.util.Set;
import tech.forethought.brick.core.event.EventListener;

/**
 * Engine configuration assembled by the instance. Immutable.
 *
 * @param listeners          extra event listeners (in addition to any
 *                           discovered through the services)
 * @param debugSnapshots     developer mode: node enter/exit events carry
 *                           full redacted data snapshots instead of key
 *                           summaries
 * @param extraSensitiveKeys additional payload keys to redact
 */
public record EngineConfig(List<EventListener> listeners, boolean debugSnapshots,
                           Set<String> extraSensitiveKeys) {

    public EngineConfig {
        listeners = List.copyOf(listeners);
        extraSensitiveKeys = Set.copyOf(extraSensitiveKeys);
    }

    /** Defaults: no extra listeners, no snapshots, no extra sensitive keys. */
    public EngineConfig() {
        this(List.of(), false, Set.of());
    }
}
