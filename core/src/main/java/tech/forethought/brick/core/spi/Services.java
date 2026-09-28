package tech.forethought.brick.core.spi;

import java.util.List;

/**
 * Runtime lookup for SPI implementations selected at assembly time. This is
 * how "at least one implementation exists" is verified: lazily, at the point
 * of use, with a precise error.
 *
 * <p>Implementations are provided by the bootstrap and are thread-safe.
 */
public interface Services {

    /**
     * Returns the implementation of {@code spiType} registered under
     * {@code name}.
     *
     * @throws IllegalStateException if none is registered under that name
     */
    <T> T require(Class<T> spiType, String name);

    /** Returns every registered implementation of {@code spiType}. */
    <T> List<T> all(Class<T> spiType);

    /**
     * Releases extension class loaders held by this registry; already-loaded
     * implementations remain usable. Default: nothing to release.
     */
    default void close() {
    }
}
