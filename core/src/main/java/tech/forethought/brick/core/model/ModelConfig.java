package tech.forethought.brick.core.model;

import java.util.Map;

/**
 * How to reach and parameterize a model. Immutable.
 *
 * @param protocol the protocol name, selecting a {@code ProtocolAdapter}
 *                 implementation (e.g. {@code "openai"})
 * @param baseUrl  endpoint base URL
 * @param apiKey   credential, supplied offline (never committed)
 * @param model    model identifier
 * @param extras   protocol-specific parameters (e.g. temperature)
 */
public record ModelConfig(String protocol, String baseUrl, String apiKey, String model,
                          Map<String, Object> extras) {

    public ModelConfig {
        extras = Map.copyOf(extras);
    }
}
