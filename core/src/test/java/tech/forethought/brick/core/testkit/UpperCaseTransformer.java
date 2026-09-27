package tech.forethought.brick.core.testkit;

import java.util.Locale;
import tech.forethought.brick.core.TextTransformer;

/** Toy TextTransformer for test-scope SPI validation. Thread-safe (stateless). */
public final class UpperCaseTransformer implements TextTransformer {

    @Override
    public String name() {
        return "upper";
    }

    @Override
    public String apply(String input) {
        return input.toUpperCase(Locale.ROOT);
    }
}
