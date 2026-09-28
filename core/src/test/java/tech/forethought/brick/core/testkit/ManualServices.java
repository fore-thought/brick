package tech.forethought.brick.core.testkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.spi.Services;

/**
 * A manually filled {@link Services} for tests. Fill during setup, then use;
 * thread-safe only after the test's setup completes.
 */
public final class ManualServices implements Services {

    private final Map<Class<?>, Map<String, Object>> byType = new HashMap<>();

    public <T> ManualServices with(Class<T> spiType, String name, T implementation) {
        byType.computeIfAbsent(spiType, t -> new HashMap<>()).put(name, implementation);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T require(Class<T> spiType, String name) {
        var byName = byType.getOrDefault(spiType, Map.of());
        var implementation = byName.get(name);
        if (implementation == null) {
            throw new IllegalStateException("no implementation of " + spiType.getName()
                    + " named '" + name + "'; available: " + byName.keySet());
        }
        return (T) implementation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> all(Class<T> spiType) {
        return byType.getOrDefault(spiType, Map.of()).values().stream()
                .map(implementation -> (T) implementation)
                .toList();
    }
}
