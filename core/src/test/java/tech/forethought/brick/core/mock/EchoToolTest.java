package tech.forethought.brick.core.mock;

import java.util.Map;
import tech.forethought.brick.core.spi.Tool;
import tech.forethought.brick.core.testkit.ToolContractTest;

public final class EchoToolTest extends ToolContractTest {

    @Override
    protected Tool subject() {
        return new EchoTool();
    }

    @Override
    protected Map<String, Object> sampleArguments() {
        return Map.of("text", "hello");
    }
}
