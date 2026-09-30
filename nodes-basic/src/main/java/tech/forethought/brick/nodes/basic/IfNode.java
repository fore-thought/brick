package tech.forethought.brick.nodes.basic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Gateway node: routes {@code "true"} or {@code "false"} by comparing an
 * input key with an expected value. Config: {@code "key"} (required),
 * {@code "equals"} (expected value). Thread-safe (stateless).
 */
public final class IfNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "if";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        // the compared key's name comes from config "key", so reads are not enumerable
        return new NodeContract(List.of(),
                List.of(new Key(EdgeKeys.ROUTE, ValueType.STRING)), true);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var key = context.config().get("key");
        if (key == null) {
            throw new IllegalArgumentException("if node requires config 'key'");
        }
        var matched = Objects.equals(input.get(String.valueOf(key)), context.config().get("equals"));
        var out = new LinkedHashMap<>(input);
        out.put(EdgeKeys.ROUTE, String.valueOf(matched));
        return out;
    }
}
