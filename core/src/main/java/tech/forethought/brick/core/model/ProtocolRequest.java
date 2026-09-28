package tech.forethought.brick.core.model;

import java.util.Map;

/**
 * A protocol-shaped request: neutral container owned by core, filled by a
 * {@code ProtocolAdapter}. Immutable.
 *
 * @param protocol the producing protocol's name
 * @param payload  protocol-specific request body
 */
public record ProtocolRequest(String protocol, Map<String, Object> payload) {

    public ProtocolRequest {
        payload = Map.copyOf(payload);
    }
}
