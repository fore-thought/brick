package tech.forethought.brick.nodes.basic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Gateway node: selective delivery by match. Reads {@code value}; config
 * {@code cases} (list of strings, required) enumerates the case pins. The
 * output map carries {@code value} under the matching case key, or under
 * {@code "default"} when nothing matches — only the selected key is
 * delivered. The case pins exist only at run time, so the contract is
 * dynamic plus the fixed {@code default} pin. Thread-safe (stateless).
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
        // case pin names come from config "cases", known only at run time
        return new NodeContract(List.of(new Key("value", ValueType.ANY)),
                List.of(new Key("default", ValueType.ANY)), true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        if (!input.containsKey("value")) {
            throw new IllegalArgumentException("switch node: input is missing key 'value'");
        }
        var cases = context.config().get("cases");
        if (!(cases instanceof List<?>)) {
            throw new IllegalArgumentException("switch node requires config 'cases'");
        }
        var selected = "default";
        for (var candidate : (List<Object>) cases) {
            if (Objects.equals(String.valueOf(candidate), String.valueOf(input.get("value")))) {
                selected = String.valueOf(candidate);
                break;
            }
        }
        var out = new LinkedHashMap<String, Object>();
        out.put(selected, input.get("value"));
        return Map.copyOf(out);
    }
}
