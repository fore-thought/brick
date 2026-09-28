package tech.forethought.brick.nodes.basic;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;

/**
 * Producer node: loads a .properties file and puts its entries onto the edge
 * data (existing keys are overwritten). Config: {@code "path"} (required);
 * relative paths resolve against the process working directory. Thread-safe
 * (stateless).
 */
public final class ConfigLoaderNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "config-loader";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var path = context.config().get("path");
        if (path == null) {
            throw new IllegalArgumentException("config-loader node requires config 'path'");
        }
        var file = Path.of(String.valueOf(path));
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "config-loader node: cannot read '" + file + "': " + e.getMessage(), e);
        }
        var out = new LinkedHashMap<>(input);
        for (var name : properties.stringPropertyNames()) {
            out.put(name, properties.getProperty(name));
        }
        return out;
    }
}
