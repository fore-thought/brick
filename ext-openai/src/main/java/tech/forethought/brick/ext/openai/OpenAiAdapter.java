package tech.forethought.brick.ext.openai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.model.ToolCall;
import tech.forethought.brick.core.model.ToolDefinition;
import tech.forethought.brick.core.spi.ProtocolAdapter;

/**
 * OpenAI chat-completions adapter. Wire format: the payload carries
 * {@code "url"}, {@code "authToken"}, and {@code "body"} (the request map);
 * responses are SSE chunks folded back into an assistant message.
 *
 * <p>Thread-safe (stateless); the HTTP client is shared. The returned
 * response stream is single-use and must be consumed on the caller's thread.
 */
public final class OpenAiAdapter implements ProtocolAdapter {

    /** The protocol name of this adapter. */
    public static final String NAME = "openai";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProtocolRequest convertRequest(List<Message> messages, List<ToolDefinition> tools,
                                          ModelConfig config) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", config.model());
        body.put("stream", true);
        body.put("messages", messages.stream().map(OpenAiAdapter::toWireMessage).toList());
        if (!tools.isEmpty()) {
            body.put("tools", tools.stream().map(OpenAiAdapter::toWireTool).toList());
        }
        body.putAll(config.extras());
        return new ProtocolRequest(NAME, Map.of(
                "url", config.baseUrl() + "/chat/completions",
                "authToken", config.apiKey(),
                "body", Map.copyOf(body)));
    }

    @Override
    public Stream<ProtocolResponse> call(ProtocolRequest request) {
        var payload = request.payload();
        var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create((String) payload.get("url")))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + payload.get("authToken"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        Json.write(payload.get("body")), StandardCharsets.UTF_8))
                .build();
        final HttpResponse<java.io.InputStream> response;
        try {
            response = CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new IllegalStateException("openai call failed: " + e.getMessage(), e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("openai call failed: HTTP " + response.statusCode()
                    + ": " + readBody(response));
        }
        var reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        return reader.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .filter(data -> !data.isEmpty())
                .takeWhile(data -> !"[DONE]".equals(data))
                .map(data -> new ProtocolResponse(NAME, readChunk(data)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Message.AssistantMessage convertResponse(Stream<ProtocolResponse> responses) {
        var content = new StringBuilder();
        var toolCalls = new LinkedHashMap<Integer, ToolCallAccumulator>();
        for (var response : responses.toList()) {
            var choices = (List<Map<String, Object>>) response.payload().get("choices");
            if (choices == null || choices.isEmpty()) {
                continue;
            }
            var delta = (Map<String, Object>) choices.getFirst().get("delta");
            if (delta == null) {
                continue;
            }
            if (delta.get("content") instanceof String text) {
                content.append(text);
            }
            if (delta.get("tool_calls") instanceof List<?> calls) {
                for (var call : (List<Map<String, Object>>) calls) {
                    var index = ((Number) call.get("index")).intValue();
                    toolCalls.computeIfAbsent(index, i -> new ToolCallAccumulator())
                            .absorb(call);
                }
            }
        }
        return new Message.AssistantMessage(content.toString(),
                toolCalls.values().stream().map(ToolCallAccumulator::build).toList());
    }

    private static Map<String, Object> toWireMessage(Message message) {
        return switch (message) {
            case Message.UserMessage m -> Map.of("role", "user", "content", m.content());
            case Message.SystemMessage m -> Map.of("role", "system", "content", m.content());
            case Message.ToolResultMessage m -> Map.of("role", "tool",
                    "tool_call_id", m.toolCallId(), "content", m.content());
            case Message.AssistantMessage m -> toWireAssistant(m);
            case null, default -> throw new IllegalArgumentException("unsupported message: " + message);
        };
    }

    private static Map<String, Object> toWireAssistant(Message.AssistantMessage message) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("role", "assistant");
        wire.put("content", message.content());
        if (!message.toolCalls().isEmpty()) {
            wire.put("tool_calls", message.toolCalls().stream()
                    .map(call -> Map.of(
                            "id", call.id(),
                            "type", "function",
                            "function", Map.of("name", call.toolName(),
                                    "arguments", Json.write(call.arguments()))))
                    .toList());
        }
        return Map.copyOf(wire);
    }

    private static Map<String, Object> toWireTool(ToolDefinition tool) {
        return Map.of("type", "function", "function", Map.of(
                "name", tool.name(),
                "description", tool.description(),
                "parameters", tool.parameterSchema()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readChunk(String data) {
        return (Map<String, Object>) Json.read(data);
    }

    private static String readBody(HttpResponse<java.io.InputStream> response) {
        try (var body = response.body()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(unreadable body)";
        }
    }

    /** Accumulates streamed tool-call deltas (arguments arrive as fragments). */
    private static final class ToolCallAccumulator {

        private final StringBuilder id = new StringBuilder();
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();

        @SuppressWarnings("unchecked")
        void absorb(Map<String, Object> delta) {
            if (delta.get("id") instanceof String value) {
                id.append(value);
            }
            if (delta.get("function") instanceof Map<?, ?> function) {
                var functionMap = (Map<String, Object>) function;
                if (functionMap.get("name") instanceof String value) {
                    name.append(value);
                }
                if (functionMap.get("arguments") instanceof String value) {
                    arguments.append(value);
                }
            }
        }

        @SuppressWarnings("unchecked")
        ToolCall build() {
            var args = arguments.isEmpty()
                    ? Map.<String, Object>of()
                    : (Map<String, Object>) Json.read(arguments.toString());
            return new ToolCall(id.toString(), name.toString(), args);
        }
    }
}
