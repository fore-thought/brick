package tech.forethought.brick.core.bootstrap;

import java.util.HashMap;
import java.util.Map;
import tech.forethought.brick.core.spi.Services;

/**
 * Registry behind {@link Services}: implementations keyed by SPI type and
 * name. Duplicate registrations fail fast at discovery.
 */
final class ServiceRegistry implements Services {

    private final Map<Class<?>, Map<String, Object>> byType = new HashMap<>();

    <T> void register(Class<T> spiType, String name, T implementation) {
        var byName = byType.computeIfAbsent(spiType, t -> new HashMap<>());
        if (byName.putIfAbsent(name, implementation) != null) {
            throw new IllegalStateException(
                    "duplicate implementation of " + spiType.getName() + " named '" + name + "'");
        }
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
}
