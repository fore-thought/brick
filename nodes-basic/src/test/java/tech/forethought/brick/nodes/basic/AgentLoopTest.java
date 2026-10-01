package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PinRef;
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
 * The M1 acceptance test under the dataflow model: an agent loop (call - with
 * tool call - back to call) executed by the engine against a mock adapter and
 * a mock tool, with a selective-delivery gateway closing the loop. No real
 * external dependency.
 */
class AgentLoopTest {

    private static final ModelConfig CONFIG =
            new ModelConfig(MockProtocolAdapter.NAME, "http://localhost", "none", "mock-model",
                    Map.of());

    /** Test node: wraps the run input text into a user message. */
    static final class InputNode implements Node {
        @Override
        public String type() {
            return "input";
        }

        @Override
        public NodeContract contract() {
            return new NodeContract(List.of(new Key("text", ValueType.STRING)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)), false);
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var out = new LinkedHashMap<String, Object>();
            out.put(EdgeKeys.MESSAGES, List.of(new Message.UserMessage(String.valueOf(input.get("text")))));
            return out;
        }
    }

    /** Test node: conversation to protocol request via the configured adapter. */
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
            var out = new LinkedHashMap<String, Object>();
            out.put("request", adapter.convertRequest((List<Message>) input.get(EdgeKeys.MESSAGES),
                    List.of(), CONFIG));
            return out;
        }
    }

    /** Test node: calls and folds the assistant message back into the conversation. */
    static final class CallNode implements Node {
        @Override
        public String type() {
            return "call";
        }

        @Override
        public NodeContract contract() {
            // messages is loop-carried context; a new request is what re-fires
            return new NodeContract(
                    List.of(new Key("request", ValueType.ANY)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST),
                            new Key("hasToolCalls", ValueType.BOOLEAN)), false);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var adapter = context.services().require(ProtocolAdapter.class, MockProtocolAdapter.NAME);
            var request = (tech.forethought.brick.core.model.ProtocolRequest) input.get("request");
            var response = adapter.convertResponse(adapter.call(request));
            var messages = new ArrayList<>(
                    (List<Message>) input.getOrDefault(EdgeKeys.MESSAGES, List.of()));
            messages.add(response);
            var out = new LinkedHashMap<String, Object>();
            out.put(EdgeKeys.MESSAGES, List.copyOf(messages));
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
            var out = new LinkedHashMap<String, Object>();
            out.put(EdgeKeys.MESSAGES, List.copyOf(history));
            return out;
        }
    }

    /** Test node: terminal collector. */
    static final class DoneNode implements Node {
        @Override
        public String type() {
            return "done";
        }

        @Override
        public NodeContract contract() {
            return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(), false);
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            return Map.of();
        }
    }

    private static EdgeSpec edge(String fromNode, String fromKey, String toNode, String toKey) {
        return new EdgeSpec(new PinRef(fromNode, fromKey), new PinRef(toNode, toKey));
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentLoopWithToolCallAndSelectiveDelivery() {
        var services = new ManualServices()
                .with(Node.class, "input", new InputNode())
                .with(Node.class, "convert-out", new ConvertOutNode())
                .with(Node.class, "call", new CallNode())
                .with(Node.class, "tool-exec", new ToolExecNode())
                .with(Node.class, "done", new DoneNode())
                .with(Node.class, IfNode.TYPE, new IfNode())
                .with(ProtocolAdapter.class, MockProtocolAdapter.NAME, new MockProtocolAdapter())
                .with(Tool.class, "echo", new EchoTool());

        var spec = new PipelineSpec("agent-loop",
                List.of(new NodeSpec("input", "input", Map.of()),
                        new NodeSpec("convert", "convert-out", Map.of()),
                        new NodeSpec("call", "call", Map.of()),
                        new NodeSpec("has-tools", IfNode.TYPE, Map.of()),
                        new NodeSpec("exec", "tool-exec", Map.of()),
                        new NodeSpec("done", "done", Map.of())),
                List.of(edge("input", EdgeKeys.MESSAGES, "convert", EdgeKeys.MESSAGES),
                        edge("input", EdgeKeys.MESSAGES, "call", EdgeKeys.MESSAGES),
                        edge("convert", "request", "call", "request"),
                        edge("call", EdgeKeys.MESSAGES, "has-tools", "value"),
                        edge("call", "hasToolCalls", "has-tools", "control"),
                        edge("has-tools", "true", "exec", EdgeKeys.MESSAGES),
                        edge("exec", EdgeKeys.MESSAGES, "convert", EdgeKeys.MESSAGES),
                        edge("exec", EdgeKeys.MESSAGES, "call", EdgeKeys.MESSAGES),
                        edge("has-tools", "false", "done", EdgeKeys.MESSAGES)),
                List.of(new PinRef("input", "text")), List.of());

        var result = new PipelineEngine(services)
                .run(spec, Map.of(new PinRef("input", "text"), "hi"));

        var messages = (List<Message>) result.get(new PinRef("call", EdgeKeys.MESSAGES));
        assertEquals(4, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertInstanceOf(Message.ToolResultMessage.class, messages.get(2));
        var last = assertInstanceOf(Message.AssistantMessage.class, messages.get(3));
        assertEquals("done", last.content());
        assertEquals(false, result.get(new PinRef("call", "hasToolCalls")));
        assertEquals(messages, result.get(new PinRef("done", EdgeKeys.MESSAGES)));
    }
}
