package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.forethought.brick.core.event.EventEmitter;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class ConfigLoaderNodeTest extends NodeContractTest {

    @TempDir
    Path dir;

    private Path propertiesFile;

    @BeforeEach
    void writeProperties() throws IOException {
        propertiesFile = dir.resolve("llm.properties");
        Files.writeString(propertiesFile, "llm.protocol=mock\nllm.model=test-model\n");
    }

    @Override
    protected Node subject() {
        return new ConfigLoaderNode();
    }

    @Override
    protected Map<String, Object> sampleConfig() {
        return Map.of("path", propertiesFile.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsEntriesAsOneMapPin() {
        var context = new NodeContext("run", "n", sampleConfig(), new ManualServices(),
                EventEmitter.noop());
        var result = subject().execute(Map.of(), context);
        var config = assertInstanceOf(Map.class, result.get("llm"));
        assertEquals("mock", ((Map<String, Object>) config).get("llm.protocol"));
        assertEquals("test-model", ((Map<String, Object>) config).get("llm.model"));
    }

    @Test
    void pinNameComesFromConfigAs() {
        var context = new NodeContext("run", "n",
                Map.of("path", propertiesFile.toString(), "as", "model"), new ManualServices(),
                EventEmitter.noop());
        var result = subject().execute(Map.of(), context);
        assertTrue(result.containsKey("model"));
    }

    @Test
    void missingFileFailsClearly() {
        var context = new NodeContext("run", "n",
                Map.of("path", dir.resolve("nope.properties").toString()), new ManualServices(),
                EventEmitter.noop());
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context));
        assertTrue(e.getMessage().contains("nope.properties"));
    }

    @Test
    void missingPathConfigFailsClearly() {
        var context = new NodeContext("run", "n", Map.of(), new ManualServices(),
                EventEmitter.noop());
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context));
        assertTrue(e.getMessage().contains("'path'"));
    }
}
