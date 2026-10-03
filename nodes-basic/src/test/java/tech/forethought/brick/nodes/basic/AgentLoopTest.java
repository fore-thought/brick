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
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spec.PipelineSpecCodec;
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
 * The M1 acceptance test under the v3 structure: the agent loop lives inside
 * a real {@link LoopNode} container whose body wires real {@link BranchNode}
 * and {@link PassNode} nodes around minimal convert/call/exec doubles, run
 * by the engine against a mock adapter and a mock tool. No real external
 * dependency.
 */
class AgentLoopTest {

    private static final ModelConfig CONFIG =
            new ModelConfig(MockProtocolAdapter.NAME, "http://localhost", "none", "mock-model",
                    Map.of());

    /** Test node: conversation to protocol request via the configured adapter. */
    static final class ConvertNode implements Node {
        @Override
        public String type() {
            return "convert";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(new Key("request", ValueType.ANY)));
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

    /** Test node: performs the protocol call. */
    static final class CallNode implements Node {
        @Override
        public String type() {
            return "call";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key("request", ValueType.ANY)),
                    List.of(new Key("responses", ValueType.LIST)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var adapter = context.services().require(ProtocolAdapter.class, MockProtocolAdapter.NAME);
            var out = new LinkedHashMap<String, Object>();
            out.put("responses",
                    adapter.call((ProtocolRequest) input.get("request")).toList());
            return out;
        }
    }

    /** Test node: folds the response into the conversation and flags continuation. */
    static final class ConvertBackNode implements Node {
        @Override
        public String type() {
            return "convert-back";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(
                    List.of(new Key("responses", ValueType.LIST),
                            new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST),
                            new Key("more", ValueType.BOOLEAN)));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            var adapter = context.services().require(ProtocolAdapter.class, MockProtocolAdapter.NAME);
            var responses = (List<ProtocolResponse>) input.get("responses");
            var assistant = adapter.convertResponse(responses.stream());
            var messages = new ArrayList<>(
                    (List<Message>) input.get(EdgeKeys.MESSAGES));
            messages.add(assistant);
            var out = new LinkedHashMap<String, Object>();
            out.put(EdgeKeys.MESSAGES, List.copyOf(messages));
            out.put("more", !assistant.toolCalls().isEmpty());
            return out;
        }
    }

    /** Test node: executes the requested tool calls and appends their results. */
    static final class ExecNode implements Node {
        @Override
        public String type() {
            return "exec";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)));
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

    private static EdgeSpec edge(String fromNode, String fromKey, String toNode, String toKey) {
        return new EdgeSpec(new PinRef(fromNode, fromKey), new PinRef(toNode, toKey));
    }

    private static Map<String, Object> agentLoopConfig() {
        var body = new PipelineSpec("agent-body",
                List.of(new NodeSpec("convert", "convert", Map.of()),
                        new NodeSpec("call", "call", Map.of()),
                        new NodeSpec("convert-back", "convert-back", Map.of()),
                        new NodeSpec("has-tools", BranchNode.TYPE, Map.of()),
                        new NodeSpec("exec", "exec", Map.of()),
                        new NodeSpec("pass", PassNode.TYPE, Map.of())),
                List.of(edge("convert", "request", "call", "request"),
                        edge("call", "responses", "convert-back", "responses"),
                        edge("convert-back", "more", "has-tools", "control"),
                        edge("convert-back", EdgeKeys.MESSAGES, "has-tools", "value"),
                        edge("has-tools", "true", "exec", EdgeKeys.MESSAGES),
                        edge("exec", EdgeKeys.MESSAGES, "pass", "value"),
                        edge("has-tools", "false", "pass", "value")),
                List.of(new PinRef("convert", EdgeKeys.MESSAGES),
                        new PinRef("convert-back", EdgeKeys.MESSAGES)),
                List.of(new PinRef("pass", "value"), new PinRef("convert-back", "more")));
        var bodyMap = PipelineSpecCodec.toMap(body);
        // the round-final conversation leaves the body via pass's "value"
        // pin; expose it on the container as "messages" so it carries
        @SuppressWarnings("unchecked")
        var outputs = (List<Map<String, Object>>) bodyMap.get("outputs");
        outputs.get(0).put("as", EdgeKeys.MESSAGES);
        var condition = new LinkedHashMap<String, Object>();
        condition.put("pin", "more");
        condition.put("initial", true);
        var config = new LinkedHashMap<String, Object>();
        config.put("body", bodyMap);
        config.put("condition", condition);
        config.put("maxIterations", 10);
        return config;
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentLoopInsideAContainerWithBranchAndPass() {
        var services = new ManualServices()
                .with(Node.class, "seed", new InputSeed())
                .with(Node.class, "convert", new ConvertNode())
                .with(Node.class, "call", new CallNode())
                .with(Node.class, "convert-back", new ConvertBackNode())
                .with(Node.class, "exec", new ExecNode())
                .with(Node.class, BranchNode.TYPE, new BranchNode())
                .with(Node.class, PassNode.TYPE, new PassNode())
                .with(Node.class, LoopNode.TYPE, new LoopNode())
                .with(Node.class, "collect", new CollectNode())
                .with(ProtocolAdapter.class, MockProtocolAdapter.NAME, new MockProtocolAdapter())
                .with(Tool.class, "echo", new EchoTool());

        var spec = new PipelineSpec("agent-loop",
                List.of(new NodeSpec("seed", "seed", Map.of()),
                        new NodeSpec("loop", LoopNode.TYPE, agentLoopConfig()),
                        new NodeSpec("collect", "collect", Map.of())),
                List.of(edge("seed", EdgeKeys.MESSAGES, "loop", EdgeKeys.MESSAGES),
                        edge("loop", EdgeKeys.MESSAGES, "collect", "value")),
                List.of(new PinRef("seed", "text")), List.of());

        var result = new PipelineEngine(services)
                .run(spec, Map.of(new PinRef("seed", "text"), "hi"));

        var messages = (List<Message>) result.get(new PinRef("loop", EdgeKeys.MESSAGES));
        assertEquals(4, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertInstanceOf(Message.ToolResultMessage.class, messages.get(2));
        var last = assertInstanceOf(Message.AssistantMessage.class, messages.get(3));
        assertEquals("done", last.content());
        assertEquals(false, result.get(new PinRef("loop", "more")));
        assertEquals(messages, result.get(new PinRef("collect", EdgeKeys.MESSAGES)));
    }

    /** Test node: wraps the run input text into a user message. */
    static final class InputSeed implements Node {
        @Override
        public String type() {
            return "seed";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key("text", ValueType.STRING)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            return Map.of(EdgeKeys.MESSAGES,
                    List.of(new Message.UserMessage(String.valueOf(input.get("text")))));
        }
    }

    /** Test node: terminal collector. */
    static final class CollectNode implements Node {
        @Override
        public String type() {
            return "collect";
        }

        @Override
        public NodeContract contract(Map<String, Object> config) {
            return new NodeContract(List.of(new Key("value", ValueType.ANY)),
                    List.of(new Key(EdgeKeys.MESSAGES, ValueType.ANY)));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
            return Map.of(EdgeKeys.MESSAGES, input.get("value"));
        }
    }
}
