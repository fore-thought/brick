package tech.forethought.brick.core;

/**
 * Transitional placeholder contract used to validate the SPI machinery:
 * service declarations, ServiceLoader discovery, and reusable contract test
 * suites. It will be replaced by real contracts when the engine lands.
 *
 * <p>Implementations must be stateless and thread-safe.
 */
public interface TextTransformer {

    /**
     * Returns the unique name of this transformer.
     *
     * @return a non-blank name
     */
    String name();

    /**
     * Transforms the given input.
     *
     * @param input the text to transform, never null
     * @return the transformed text, never null
     * @throws NullPointerException if {@code input} is null
     */
    String apply(String input);
}
