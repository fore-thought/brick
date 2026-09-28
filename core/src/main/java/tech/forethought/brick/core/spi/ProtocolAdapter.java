package tech.forethought.brick.core.spi;

import java.util.List;
import java.util.stream.Stream;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.model.ToolDefinition;

/**
 * The capability of one model protocol: converts between domain messages
 * and protocol-shaped payloads, and performs the actual call. All protocol
 * specifics are encapsulated here; core and nodes never see them.
 *
 * <p>Implementations are discovered via {@code ServiceLoader}, must have a
 * public no-arg constructor, and must be thread-safe; instances are shared.
 */
public interface ProtocolAdapter {

    /**
     * The protocol name (e.g. {@code "openai"}), as referenced by
     * {@code ModelConfig.protocol}. Names are protocol: renaming is a
     * breaking change.
     */
    String name();

    /** Outbound conversion: conversation context to protocol request. */
    ProtocolRequest convertRequest(List<Message> messages, List<ToolDefinition> tools,
                                   ModelConfig config);

    /**
     * Performs the call. Responses stream in protocol shape; protocol
     * specifics such as SSE chunking are the implementation's business.
     * The returned stream is single-use and consumed on the caller's thread.
     */
    Stream<ProtocolResponse> call(ProtocolRequest request);

    /** Inbound conversion: folds the response stream into an assistant message. */
    Message.AssistantMessage convertResponse(Stream<ProtocolResponse> responses);
}
