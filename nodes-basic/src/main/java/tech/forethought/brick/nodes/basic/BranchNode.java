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
 * Gateway node: selective delivery by match. Reads {@code control} and
 * {@code value}; config {@code cases} (list of strings, optional) enumerates
 * the case pins. The output map carries {@code value} under the case whose
 * name equals the string form of {@code control}, or under {@code "default"}
 * when nothing matches — only the selected key is delivered, so the untaken
 * branch's subgraph never fires. Without config, a BOOLEAN control degrades
 * to the two-pin form (cases {@code ["true", "false"]}); any other control
 * without config is an error. The case pins exist only at run time, so the
 * contract is dynamic plus the fixed {@code default} pin. Thread-safe
 * (stateless).
 */
public final class BranchNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "branch";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract(Map<String, Object> config) {
        // case pin names come from config "cases", known only at run time
        return new NodeContract(
                List.of(new Key("control", ValueType.ANY), new Key("value", ValueType.ANY)),
                List.of(new Key("default", ValueType.ANY)), true);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        if (!input.containsKey("control")) {
            throw new IllegalArgumentException("branch node: input is missing key 'control'");
        }
        if (!input.containsKey("value")) {
            throw new IllegalArgumentException("branch node: input is missing key 'value'");
        }
        var control = input.get("control");
        List<String> cases = resolveCases(context, control);
        var selected = "default";
        for (var candidate : cases) {
            if (Objects.equals(candidate, String.valueOf(control))) {
                selected = candidate;
                break;
            }
        }
        var out = new LinkedHashMap<String, Object>();
        out.put(selected, input.get("value"));
        return Map.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static List<String> resolveCases(NodeContext context, Object control) {
        var configured = context.config().get("cases");
        if (configured == null) {
            if (control instanceof Boolean) {
                return List.of("true", "false");
            }
            throw new IllegalArgumentException(
                    "branch node requires config 'cases' unless control is a boolean");
        }
        if (!(configured instanceof List<?> list)) {
            throw new IllegalArgumentException("branch node requires config 'cases' as a list");
        }
        return (List<String>) list;
    }
}
