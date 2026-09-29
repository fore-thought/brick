package tech.forethought.brick.core.bootstrap;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.spi.Services;

/**
 * Registry behind {@link Services}: implementations keyed by SPI type and
 * name. Duplicate registrations fail fast at discovery. Owns and closes the
 * extension loaders created during discovery.
 *
 * <p>Lifecycle: mutable during assembly, then {@link #freeze()} publishes an
 * immutable snapshot. Lookups and {@link #close()} are thread-safe once frozen
 * (the snapshot is published through a volatile reference); registrations
 * after freezing are rejected.
 */
final class ServiceRegistry implements Services {

    /** Immutable registry state published by {@link #freeze()}. */
    private record Frozen(
            Map<Class<?>, Map<String, Object>> byType,
            List<URLClassLoader> extensionLoaders) {
    }

    private final Map<Class<?>, Map<String, Object>> byType = new HashMap<>();
    private final List<URLClassLoader> extensionLoaders = new ArrayList<>();
    private volatile Frozen frozen;

    <T> void register(Class<T> spiType, String name, T implementation) {
        requireMutable();
        var byName = byType.computeIfAbsent(spiType, t -> new HashMap<>());
        if (byName.putIfAbsent(name, implementation) != null) {
            throw new IllegalStateException(
                    "duplicate implementation of " + spiType.getName() + " named '" + name + "'");
        }
    }

    void addExtensionLoader(URLClassLoader loader) {
        requireMutable();
        extensionLoaders.add(loader);
    }

    /** Publishes the assembled state as immutable; rejects registrations afterwards. */
    void freeze() {
        requireMutable();
        var frozenByType = new HashMap<Class<?>, Map<String, Object>>();
        byType.forEach((spiType, byName) -> frozenByType.put(spiType, Map.copyOf(byName)));
        frozen = new Frozen(Map.copyOf(frozenByType), List.copyOf(extensionLoaders));
    }

    @Override
    public void close() {
        for (var loader : requireFrozen().extensionLoaders()) {
            try {
                loader.close();
            } catch (IOException e) {
                // best effort
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T require(Class<T> spiType, String name) {
        var byName = requireFrozen().byType().getOrDefault(spiType, Map.of());
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
        return requireFrozen().byType().getOrDefault(spiType, Map.of()).values().stream()
                .map(implementation -> (T) implementation)
                .toList();
    }

    private Frozen requireFrozen() {
        var snapshot = frozen;
        if (snapshot == null) {
            throw new IllegalStateException("registry is not frozen");
        }
        return snapshot;
    }

    private void requireMutable() {
        if (frozen != null) {
            throw new IllegalStateException("registry is frozen");
        }
    }
}
