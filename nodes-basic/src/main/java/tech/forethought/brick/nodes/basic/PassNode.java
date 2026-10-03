package tech.forethought.brick.nodes.basic;

import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Join node: passes its {@code value} read straight through to its
 * {@code value} write. Built for spots where several producers deliver the
 * same logical value onto one pin: every arrival triggers a firing and the
 * engine's sticky bindings guarantee last-write-wins. Thread-safe
 * (stateless).
 */
public final class PassNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "pass";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract(Map<String, Object> config) {
        return new NodeContract(List.of(new Key("value", ValueType.ANY)),
                List.of(new Key("value", ValueType.ANY)));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        if (!input.containsKey("value")) {
            throw new IllegalArgumentException("pass node: input is missing key 'value'");
        }
        return Map.of("value", input.get("value"));
    }
}
