package tech.forethought.brick.nodes.basic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Producer node: loads a .properties file and writes its entries as one MAP
 * pin, so a single wire carries the whole configuration object. Config:
 * {@code "path"} (required; relative paths resolve against the process
 * working directory) and {@code "as"} (the pin name, default {@code "llm"}).
 * Thread-safe (stateless).
 */
public final class ConfigLoaderNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "config-loader";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        // the pin name comes from config "as", known only at run time
        return new NodeContract(List.of(), List.of(new Key("llm", ValueType.MAP)), true);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var path = context.config().get("path");
        if (path == null) {
            throw new IllegalArgumentException("config-loader node requires config 'path'");
        }
        var file = Path.of(String.valueOf(path));
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "config-loader node: cannot read '" + file + "': " + e.getMessage(), e);
        }
        var config = new LinkedHashMap<String, Object>();
        for (var name : properties.stringPropertyNames()) {
            config.put(name, properties.getProperty(name));
        }
        var out = new LinkedHashMap<String, Object>();
        out.put(String.valueOf(context.config().getOrDefault("as", "llm")), Map.copyOf(config));
        return Map.copyOf(out);
    }
}
