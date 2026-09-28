package tech.forethought.brick.store.jsonl;

import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import tech.forethought.brick.core.spi.StateStore;
import tech.forethought.brick.core.testkit.StateStoreContractTest;

public final class JsonlEventStoreReadTest extends StateStoreContractTest {

    @TempDir
    Path dir;

    @Override
    protected StateStore subject() {
        return new JsonlEventStore(dir);
    }
}
