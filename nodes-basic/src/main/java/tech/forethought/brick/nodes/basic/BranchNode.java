package tech.forethought.brick.nodes.basic;

import java.util.ArrayList;
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
 * When nothing matches, {@code value} is delivered under {@code "default"}.
 * Without config, a BOOLEAN control degrades to the two-pin form (cases
 * {@code ["true", "false"]}); any other control without config is an error.
 * All pins are enumerated from the configuration — none are implicit.
 * Thread-safe (stateless).
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
        var cases = config.get("cases");
        var writes = new ArrayList<Key>();
        if (cases instanceof List<?> list) {
            for (var name : list) {
                writes.add(new Key(String.valueOf(name), ValueType.ANY));
            }
        } else {
            // no config: the BOOLEAN-control degradation pins
            writes.add(new Key("true", ValueType.ANY));
            writes.add(new Key("false", ValueType.ANY));
        }
        writes.add(new Key("default", ValueType.ANY));
        return new NodeContract(
                List.of(new Key("control", ValueType.ANY), new Key("value", ValueType.ANY)),
                writes);
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
