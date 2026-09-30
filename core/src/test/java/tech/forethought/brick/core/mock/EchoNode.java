package tech.forethought.brick.core.mock;

import java.util.Map;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;

/** Passes edge data through unchanged. Thread-safe (stateless). */
public final class EchoNode implements Node {

    @Override
    public String type() {
        return "echo";
    }

    @Override
    public NodeContract contract() {
        return NodeContract.empty();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        return input;
    }
}
