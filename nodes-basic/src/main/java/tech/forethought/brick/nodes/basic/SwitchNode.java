package tech.forethought.brick.nodes.basic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Gateway node: routes by the string form of an input key's value. Config:
 * {@code "key"} (required). Thread-safe (stateless).
 */
public final class SwitchNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "switch";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        // the routed key's name comes from config "key", so reads are not enumerable
        return new NodeContract(List.of(),
                List.of(new Key(EdgeKeys.ROUTE, ValueType.STRING)), true);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var key = context.config().get("key");
        if (key == null) {
            throw new IllegalArgumentException("switch node requires config 'key'");
        }
        var value = input.get(String.valueOf(key));
        if (value == null) {
            throw new IllegalArgumentException("switch node: input is missing key '" + key + "'");
        }
        var out = new LinkedHashMap<>(input);
        out.put(EdgeKeys.ROUTE, String.valueOf(value));
        return out;
    }
}
