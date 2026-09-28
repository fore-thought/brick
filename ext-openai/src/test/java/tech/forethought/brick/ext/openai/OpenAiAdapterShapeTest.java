package tech.forethought.brick.ext.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.model.ToolCall;
import tech.forethought.brick.core.model.ToolDefinition;

class OpenAiAdapterShapeTest {

    private static final ModelConfig CONFIG =
            new ModelConfig(OpenAiAdapter.NAME, "http://127.0.0.1:1", "key", "gpt-test", Map.of());

    private final OpenAiAdapter adapter = new OpenAiAdapter();

    @Test
    @SuppressWarnings("unchecked")
    void convertRequestCarriesConnectionAndBody() {
        var request = adapter.convertRequest(
                List.of(new Message.SystemMessage("sys"), new Message.UserMessage("hi")),
                List.of(), CONFIG);
        assertEquals("openai", request.protocol());
        assertEquals("http://127.0.0.1:1/chat/completions", request.payload().get("url"));
        assertEquals("key", request.payload().get("authToken"));
        var body = (Map<String, Object>) request.payload().get("body");
        assertEquals("gpt-test", body.get("model"));
        assertEquals(true, body.get("stream"));
        var messages = (List<Map<String, Object>>) body.get("messages");
        assertEquals(Map.of("role", "system", "content", "sys"), messages.get(0));
        assertEquals(Map.of("role", "user", "content", "hi"), messages.get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertRequestMapsAssistantToolCallsAndToolResults() {
        var request = adapter.convertRequest(List.of(
                new Message.AssistantMessage("", List.of(
                        new ToolCall("c1", "echo", Map.of("text", "hi")))),
                new Message.ToolResultMessage("c1", "ok")), List.of(), CONFIG);
        var body = (Map<String, Object>) request.payload().get("body");
        var messages = (List<Map<String, Object>>) body.get("messages");
        var assistant = messages.get(0);
        var toolCalls = (List<Map<String, Object>>) assistant.get("tool_calls");
        var function = (Map<String, Object>) toolCalls.getFirst().get("function");
        assertEquals("echo", function.get("name"));
        assertEquals("{\"text\":\"hi\"}", function.get("arguments"));
        assertEquals("tool", messages.get(1).get("role"));
        assertEquals("c1", messages.get(1).get("tool_call_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertRequestMergesExtrasAndSerializesTools() {
        var config = new ModelConfig(OpenAiAdapter.NAME, "http://127.0.0.1:1", "key", "gpt-test",
                Map.of("temperature", 0.7));
        var request = adapter.convertRequest(List.of(new Message.UserMessage("hi")),
                List.of(new ToolDefinition("echo", "Echoes", Map.of("type", "object"))), config);
        var body = (Map<String, Object>) request.payload().get("body");
        assertEquals(0.7, body.get("temperature"));
        var tools = (List<Map<String, Object>>) body.get("tools");
        var function = (Map<String, Object>) tools.getFirst().get("function");
        assertEquals("echo", function.get("name"));
        assertEquals(Map.of("type", "object"), function.get("parameters"));
    }

    @Test
    void convertResponseFoldsContentAndToolCallFragments() {
        var contentHead = chunk(Map.of("content", "Hel"));
        var toolName = chunk(Map.of("tool_calls", List.of(
                Map.of("index", 0, "id", "c1", "function", Map.of("name", "echo")))));
        var argumentsHead = chunk(Map.of("tool_calls", List.of(
                Map.of("index", 0, "function", Map.of("arguments", "{\"text\":\"he")))));
        var argumentsTail = chunk(Map.of("tool_calls", List.of(
                Map.of("index", 0, "function", Map.of("arguments", "llo\"}")))));
        var contentTail = chunk(Map.of("content", "lo"));

        var message = adapter.convertResponse(Stream.of(
                contentHead, toolName, argumentsHead, argumentsTail, contentTail));

        assertEquals("Hello", message.content());
        assertEquals(List.of(new ToolCall("c1", "echo", Map.of("text", "hello"))),
                message.toolCalls());
    }

    @Test
    void httpErrorFailsWithStatusAndBody() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            var body = "{\"error\":\"nope\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var request = adapter.convertRequest(List.of(new Message.UserMessage("hi")),
                    List.of(), new ModelConfig(OpenAiAdapter.NAME,
                            "http://127.0.0.1:" + server.getAddress().getPort(), "key", "m",
                            Map.of()));
            var e = assertThrows(IllegalStateException.class, () -> adapter.call(request));
            assertTrue(e.getMessage().contains("429"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void textDeltaExtractsIncrementalContent() {
        var withText = new ProtocolResponse("openai",
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "Hel")))));
        assertEquals("Hel", adapter.textDelta(withText));
        var empty = new ProtocolResponse("openai",
                Map.of("choices", List.of(Map.of("delta", Map.of()))));
        assertEquals("", adapter.textDelta(empty));
    }

    private static ProtocolResponse chunk(Map<String, Object> delta) {
        return new ProtocolResponse("openai", Map.of("choices", List.of(Map.of("delta", delta))));
    }
}
