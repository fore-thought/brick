package tech.forethought.brick.core.testkit;

import tech.forethought.brick.core.TextTransformer;

final class ReverseTransformerTest extends TextTransformerContractTest {

    @Override
    protected TextTransformer subject() {
        return new ReverseTransformer();
    }
}
