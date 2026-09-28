package tech.forethought.brick.core.model;

import java.util.Map;

/**
 * A protocol-shaped response chunk: neutral container owned by core,
 * produced and interpreted by a {@code ProtocolAdapter}. Immutable.
 *
 * @param protocol the producing protocol's name
 * @param payload  protocol-specific response chunk
 */
public record ProtocolResponse(String protocol, Map<String, Object> payload) {

    public ProtocolResponse {
        payload = Map.copyOf(payload);
    }
}
