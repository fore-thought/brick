package tech.forethought.brick.ext.openai;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.testkit.ProtocolAdapterContractTest;

/**
 * Runs the shared protocol-adapter contract suite against {@link OpenAiAdapter},
 * backed by a local fake server (no real network, no credentials).
 */
public final class OpenAiAdapterTest extends ProtocolAdapterContractTest {

    private static HttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            var body = """
                    data: {"choices":[{"delta":{"content":"hi"}}]}

                    data: [DONE]

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Override
    protected ProtocolAdapter subject() {
        return new OpenAiAdapter();
    }

    @Override
    protected ModelConfig sampleConfig() {
        return new ModelConfig(OpenAiAdapter.NAME, baseUrl, "test-key", "gpt-test", Map.of());
    }
}
