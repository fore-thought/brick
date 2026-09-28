package tech.forethought.brick.core.model;

import java.util.Map;

/**
 * A tool invocation requested by the model. Immutable.
 *
 * @param id        correlates the call with its {@code ToolResultMessage}
 * @param toolName  the tool's name as in its {@link ToolDefinition}
 * @param arguments model-supplied arguments
 */
public record ToolCall(String id, String toolName, Map<String, Object> arguments) {

    public ToolCall {
        arguments = Map.copyOf(arguments);
    }
}
