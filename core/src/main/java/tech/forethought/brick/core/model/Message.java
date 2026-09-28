package tech.forethought.brick.core.model;

import java.util.List;

/**
 * A message in a conversation. Closed hierarchy: user, system, assistant,
 * and tool-result messages. All implementations are immutable and
 * thread-safe.
 */
public sealed interface Message {

    /** A message from the end user. */
    record UserMessage(String content) implements Message {
    }

    /** A system-prompt message. */
    record SystemMessage(String content) implements Message {
    }

    /** A message produced by the model: text plus any requested tool calls. */
    record AssistantMessage(String content, List<ToolCall> toolCalls) implements Message {
        public AssistantMessage {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    /** The result of executing a tool call, fed back into the conversation. */
    record ToolResultMessage(String toolCallId, String content) implements Message {
    }
}
