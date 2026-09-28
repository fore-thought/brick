package tech.forethought.brick.core.mock;

import java.util.Map;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;

/**
 * Test double for hot-plug loading: not declared in the platform's test
 * services, so it only becomes discoverable from an extension jar.
 * Thread-safe (stateless).
 */
public final class PlugNode implements Node {

    @Override
    public String type() {
        return "plug";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        return input;
    }
}
