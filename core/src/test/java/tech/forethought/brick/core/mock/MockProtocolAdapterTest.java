package tech.forethought.brick.core.mock;

import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.testkit.ProtocolAdapterContractTest;

public final class MockProtocolAdapterTest extends ProtocolAdapterContractTest {

    @Override
    protected ProtocolAdapter subject() {
        return new MockProtocolAdapter();
    }

    @Override
    protected ModelConfig sampleConfig() {
        return new ModelConfig(MockProtocolAdapter.NAME, "http://localhost", "none",
                "mock-model", Map.of());
    }
}
