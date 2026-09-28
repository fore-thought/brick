package tech.forethought.brick.core.mock;

import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class EchoNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new EchoNode();
    }
}
