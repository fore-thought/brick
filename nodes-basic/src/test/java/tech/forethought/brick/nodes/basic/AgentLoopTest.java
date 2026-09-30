package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.engine.PipelineEngine;
import tech.forethought.brick.core.mock.EchoTool;
import tech.forethought.brick.core.mock.MockProtocolAdapter;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.spi.Tool;
import tech.forethought.brick.core.testkit.ManualServices;

/**
 * The M1 acceptance test: an agent loop (call - with tool call - back to
 * call) executed by the engine against a mock adapter and a mock tool, with
 * gateway nodes routing the loop. No real external dependency.
 */
class AgentLoopTest {

    private static final ModelConfig CONFIG =
            new ModelConfig(MockProtocolAdapter.NAME, "http://localhost", "none", "mock-model",
                    Map.of());

    /** Test node: seeds the conversation with a user message. */
    static final class InputNode implements Node {
        @Override
        public String type() {
            return "input";
        }

        @Override
        public NodeContract contract() {
            return new NodeContract(List.of(),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)), false);
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var out = new LinkedHashMap<>(input);
            out.put(EdgeKeys.MESSAGES, List.of(new Message.UserMessage("hi")));
            return out;
        }
    }

    /** Test node: message context to protocol request via the configured adapter. */
    static final class ConvertOutNode implements Node {
        @Override
        public String type() {
            return "convert-out";
        }

        @Override
        public NodeContract contract() {
            return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(new Key("request", ValueType.ANY)), false);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var adapter = context.services().require(ProtocolAdapter.class, MockProtocolAdapter.NAME);
            var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
            var out = new LinkedHashMap<>(input);
            out.put("request", adapter.convertRequest(messages, List.of(), CONFIG));
            return out;
        }
    }

    /** Test node: converts, calls, folds, and appends the assistant message. */
    static final class CallNode implements Node {
        @Override
        public String type() {
            return "call";
        }

        @Override
        public NodeContract contract() {
            return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST),
                            new Key("hasToolCalls", ValueType.BOOLEAN)), false);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var adapter = context.services().require(ProtocolAdapter.class, MockProtocolAdapter.NAME);
            var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
            // reconvert so the adapter sees the current conversation on loop-back
            var request = adapter.convertRequest(messages, List.of(), CONFIG);
            var response = adapter.convertResponse(adapter.call(request));
            var history = new ArrayList<>(messages);
            history.add(response);
            var out = new LinkedHashMap<>(input);
            out.put(EdgeKeys.MESSAGES, List.copyOf(history));
            out.put("hasToolCalls", !response.toolCalls().isEmpty());
            return out;
        }
    }

    /** Test node: executes the requested tool calls and appends their results. */
    static final class ToolExecNode implements Node {
        @Override
        public String type() {
            return "tool-exec";
        }

        @Override
        public NodeContract contract() {
            return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)), false);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
            var last = assertInstanceOf(Message.AssistantMessage.class, messages.getLast());
            var history = new ArrayList<>(messages);
            for (var call : last.toolCalls()) {
                var tool = context.services().require(Tool.class, call.toolName());
                history.add(new Message.ToolResultMessage(call.id(),
                        String.valueOf(tool.execute(call.arguments()))));
            }
            var out = new LinkedHashMap<>(input);
            out.put(EdgeKeys.MESSAGES, List.copyOf(history));
            return out;
        }
    }

    /** Test node: terminal no-op. */
    static final class NoopNode implements Node {
        @Override
        public String type() {
            return "noop";
        }

        @Override
        public NodeContract contract() {
            return NodeContract.empty();
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            return input;
        }
    }

    @Test
    void agentLoopWithToolCallAndBackEdge() {
        var services = new ManualServices()
                .with(Node.class, "input", new InputNode())
                .with(Node.class, "convert-out", new ConvertOutNode())
                .with(Node.class, "call", new CallNode())
                .with(Node.class, "tool-exec", new ToolExecNode())
                .with(Node.class, "noop", new NoopNode())
                .with(Node.class, IfNode.TYPE, new IfNode())
                .with(ProtocolAdapter.class, MockProtocolAdapter.NAME, new MockProtocolAdapter())
                .with(Tool.class, "echo", new EchoTool());

        var spec = new PipelineSpec("agent-loop",
                List.of(new NodeSpec("input", "input", Map.of()),
                        new NodeSpec("convert", "convert-out", Map.of()),
                        new NodeSpec("call", "call", Map.of()),
                        new NodeSpec("has-tools", IfNode.TYPE,
                                Map.of("key", "hasToolCalls", "equals", true)),
                        new NodeSpec("exec", "tool-exec", Map.of()),
                        new NodeSpec("done", "noop", Map.of())),
                List.of(new EdgeSpec("input", "convert", null),
                        new EdgeSpec("convert", "call", null),
                        new EdgeSpec("call", "has-tools", null),
                        new EdgeSpec("has-tools", "exec", "true"),
                        new EdgeSpec("has-tools", "done", "false"),
                        new EdgeSpec("exec", "call", null)),
                "input");

        var result = new PipelineEngine(services).run(spec, Map.of());

        @SuppressWarnings("unchecked")
        var messages = (List<Message>) result.get(EdgeKeys.MESSAGES);
        assertEquals(4, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertInstanceOf(Message.ToolResultMessage.class, messages.get(2));
        var last = assertInstanceOf(Message.AssistantMessage.class, messages.get(3));
        assertEquals("done", last.content());
        assertEquals(false, result.get("hasToolCalls"));
        assertFalse(result.containsKey(EdgeKeys.ROUTE));
    }
}
