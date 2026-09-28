package tech.forethought.brick.core.spi;

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
}
