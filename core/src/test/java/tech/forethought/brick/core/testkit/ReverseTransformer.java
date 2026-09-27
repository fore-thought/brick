package tech.forethought.brick.core.testkit;

import tech.forethought.brick.core.TextTransformer;

/** Toy TextTransformer for test-scope SPI validation. Thread-safe (stateless). */
public final class ReverseTransformer implements TextTransformer {

    @Override
    public String name() {
        return "reverse";
    }

    @Override
    public String apply(String input) {
        return new StringBuilder(input).reverse().toString();
    }
}
