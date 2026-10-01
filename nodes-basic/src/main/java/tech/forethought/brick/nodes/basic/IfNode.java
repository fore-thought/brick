package tech.forethought.brick.nodes.basic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Gateway node: selective delivery by truthiness. Reads {@code control}
 * (BOOLEAN) and {@code value} (ANY); the output map carries {@code value}
 * under {@code "true"} or {@code "false"} only — the engine delivers along
 * out-edges per key, so the untaken branch's pins never receive a value and
 * their subgraph never fires. No config. Thread-safe (stateless).
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
        return new NodeContract(
                List.of(new Key("control", ValueType.BOOLEAN), new Key("value", ValueType.ANY)),
                List.of(new Key("true", ValueType.ANY), new Key("false", ValueType.ANY)), false);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        if (!input.containsKey("control")) {
            throw new IllegalArgumentException("if node: input is missing key 'control'");
        }
        var out = new LinkedHashMap<String, Object>();
        out.put(Boolean.TRUE.equals(input.get("control")) ? "true" : "false",
                input.get("value"));
        return Map.copyOf(out);
    }
}
