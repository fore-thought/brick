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
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpecCodec;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.spi.Tool;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.nodes.basic.BranchNode;
import tech.forethought.brick.nodes.basic.ConfigLoaderNode;
import tech.forethought.brick.nodes.basic.LoopNode;
import tech.forethought.brick.nodes.basic.PassNode;

/**
 * The default chat graph end-to-end: config loading, the agent loop inside
 * the loop container (one tool-call round against the mock adapter), and the
 * output terminal — all against test doubles.
 */
class DefaultSpecsTest {

    @TempDir
    Path dir;

    private static ManualServices services() {
        return new ManualServices()
                .with(Node.class, ConfigLoaderNode.TYPE, new ConfigLoaderNode())
                .with(Node.class, BranchNode.TYPE, new BranchNode())
                .with(Node.class, LoopNode.TYPE, new LoopNode())
                .with(Node.class, PassNode.TYPE, new PassNode())
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
    void defaultChatSpecRoundTripsThroughCodec() {
        var spec = DefaultSpecs.chat();
        assertEquals(spec, PipelineSpecCodec.read(PipelineSpecCodec.write(spec)));
    }

    @Test
    void customConfigPathReplacesOnlyTheConfigNode() {
        var spec = DefaultSpecs.chat("custom/path.properties");
        assertEquals(DefaultSpecs.chat().edges(), spec.edges());
        assertEquals(DefaultSpecs.chat().inputs(), spec.inputs());
        assertEquals(DefaultSpecs.chat().outputs(), spec.outputs());
        assertEquals(DefaultSpecs.chat().nodes().size(), spec.nodes().size());
        var configNode = spec.nodes().stream().filter(node -> node.id().equals("config"))
                .findFirst().orElseThrow();
        assertEquals(Map.of("path", "custom/path.properties"), configNode.config());
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
                .run(DefaultSpecs.chat(propertiesFile.toString()),
                        Map.of(new PinRef("input", AgentKeys.INPUT), "hi",
                                new PinRef("input", AgentKeys.HISTORY), List.of()));

        assertEquals("done", result.get(new PinRef("out", AgentKeys.OUTPUT)));
        assertEquals(false, result.get(new PinRef("agent-loop", AgentKeys.HAS_TOOL_CALLS)));
        var messages = (List<Message>) result.get(new PinRef("out", EdgeKeys.MESSAGES));
        assertEquals(4, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertInstanceOf(Message.ToolResultMessage.class, messages.get(2));
        var last = assertInstanceOf(Message.AssistantMessage.class, messages.get(3));
        assertEquals("done", last.content());
        assertFalse(result.containsKey(new PinRef("agent-loop", "true")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultChatGraphCarriesInjectedHistory() throws IOException {
        var propertiesFile = dir.resolve("llm.properties");
        Files.writeString(propertiesFile, """
                llm.protocol=mock
                llm.base-url=http://localhost
                llm.api-key=none
                llm.model=mock-model
                """);

        var history = List.<Message>of(
                new Message.UserMessage("earlier question"),
                new Message.AssistantMessage("earlier answer", List.of()));
        var result = new PipelineEngine(services())
                .run(DefaultSpecs.chat(propertiesFile.toString()),
                        Map.of(new PinRef("input", AgentKeys.INPUT), "hi",
                                new PinRef("input", AgentKeys.HISTORY), history));

        var messages = (List<Message>) result.get(new PinRef("out", EdgeKeys.MESSAGES));
        // history(2) + user + assistant(tool call) + tool result + assistant("done")
        assertEquals(6, messages.size());
        assertEquals(new Message.UserMessage("earlier question"), messages.get(0));
        assertEquals(new Message.AssistantMessage("earlier answer", List.of()), messages.get(1));
        assertEquals("done", result.get(new PinRef("out", AgentKeys.OUTPUT)));
    }
}
