package tech.forethought.brick.core.spi;

import java.util.Map;
import tech.forethought.brick.core.model.ToolDefinition;

/**
 * A tool callable by the model.
 *
 * <p>Implementations are discovered via {@code ServiceLoader}, must have a
 * public no-arg constructor, and must be thread-safe.
 */
public interface Tool {

    /** Name, description, and parameter schema presented to the model. */
    ToolDefinition definition();

    /**
     * Executes the tool with model-supplied arguments. The result is
     * stringified when fed back to the model.
     */
    Object execute(Map<String, Object> arguments);
}
