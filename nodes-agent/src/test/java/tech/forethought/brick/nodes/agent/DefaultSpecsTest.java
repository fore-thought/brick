package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.forethought.brick.core.engine.PipelineEngine;
import tech.forethought.brick.core.engine.SpecValidator;
import tech.forethought.brick.core.mock.EchoTool;
import tech.forethought.brick.core.mock.MockProtocolAdapter;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.spi.Tool;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.nodes.basic.ConfigLoaderNode;
import tech.forethought.brick.nodes.basic.IfNode;

/**
 * The default chat graph end-to-end: config loading, agent loop with a tool
 * call and a back-edge, and the output terminal — all against test doubles.
 */
class DefaultSpecsTest {

    @TempDir
    Path dir;

    private static ManualServices services() {
        return new ManualServices()
                .with(Node.class, ConfigLoaderNode.TYPE, new ConfigLoaderNode())
                .with(Node.class, IfNode.TYPE, new IfNode())
                .with(Node.class, InputNode.TYPE, new InputNode())
                .with(Node.class, ContextPreprocessNode.TYPE, new ContextPreprocessNode())
                .with(Node.class, ConvertOutNode.TYPE, new ConvertOutNode())
                .with(Node.class, CallNode.TYPE, new CallNode())
                .with(Node.class, ConvertInNode.TYPE, new ConvertInNode())
                .with(Node.class, ToolExecNode.TYPE, new ToolExecNode())
                .with(Node.class, OutputNode.TYPE, new OutputNode())
                .with(ProtocolAdapter.class, MockProtocolAdapter.NAME, new MockProtocolAdapter())
                .with(Tool.class, "echo", new EchoTool());
    }

    @Test
    void defaultChatSpecIsStructurallyClean() {
        assertTrue(SpecValidator.validate(DefaultSpecs.chat()).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultChatGraphRunsTheAgentLoop() throws IOException {
        var propertiesFile = dir.resolve("llm.properties");
        Files.writeString(propertiesFile, """
                llm.protocol=mock
                llm.base-url=http://localhost
                llm.api-key=none
                llm.model=mock-model
                """);

        var result = new PipelineEngine(services())
                .run(DefaultSpecs.chat(propertiesFile.toString()), Map.of(AgentKeys.INPUT, "hi"));

        assertEquals("done", result.get(AgentKeys.OUTPUT));
        assertEquals(false, result.get(AgentKeys.HAS_TOOL_CALLS));
        assertFalse(result.containsKey(EdgeKeys.ROUTE));
        var messages = (List<Message>) result.get(EdgeKeys.MESSAGES);
        assertEquals(4, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertInstanceOf(Message.ToolResultMessage.class, messages.get(2));
        assertInstanceOf(Message.AssistantMessage.class, messages.get(3));
    }
}
