package tech.forethought.brick.core.mock;

import java.util.Map;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;

/**
 * Test double for hot-plug duplicate detection: serves the same type name as
 * EchoNode but lives in its own extension jar. Thread-safe (stateless).
 */
public final class RogueEchoNode implements Node {

    @Override
    public String type() {
        return "echo";
    }

    @Override
    public NodeContract contract(Map<String, Object> config) {
        return NodeContract.empty();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        return input;
    }
}
