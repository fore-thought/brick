package tech.forethought.brick.core.mock;

import java.util.Map;
import tech.forethought.brick.core.model.ToolDefinition;
import tech.forethought.brick.core.spi.Tool;

/** Echoes its arguments back. Thread-safe (stateless). */
public final class EchoTool implements Tool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("echo", "Echoes arguments back", Map.of());
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return Map.copyOf(arguments);
    }
}
