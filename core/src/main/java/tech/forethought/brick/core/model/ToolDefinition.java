package tech.forethought.brick.core.model;

import java.util.Map;

/**
 * The description of a tool as presented to the model. Immutable.
 *
 * @param name            unique tool name
 * @param description     human- and model-readable purpose
 * @param parameterSchema JSON-Schema-shaped parameter description
 */
public record ToolDefinition(String name, String description, Map<String, Object> parameterSchema) {

    public ToolDefinition {
        parameterSchema = Map.copyOf(parameterSchema);
    }
}
