package tech.forethought.brick.store.jsonl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.event.EventListener;
import tech.forethought.brick.core.event.TraceEvent;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.MessageCodec;
import tech.forethought.brick.core.testkit.EventListenerContractTest;

public final class JsonlEventStoreTest extends EventListenerContractTest {

    @TempDir
    Path dir;

    @Override
    protected EventListener subject() {
        return new JsonlEventStore(dir);
    }

    @Test
    void skipsTransientEvents() throws IOException {
        var store = new JsonlEventStore(dir);
        store.onEvent(new TraceEvent(EventKinds.RUN_START, 1L, "r1", "s1", null, Map.of()));
        store.onEvent(new TraceEvent(EventKinds.TOKEN_DELTA, 2L, "r1", "s1", "call",
                Map.of("text", "x")));
        var lines = Files.readAllLines(dir.resolve("s1.jsonl"));
        assertEquals(1, lines.size());
    }

    @Test
    void replaysMessageAppendedEvents() {
        var store = new JsonlEventStore(dir);
        store.onEvent(messageEvent(new Message.UserMessage("hi")));
        store.onEvent(messageEvent(new Message.AssistantMessage("hello", List.of())));
        var messages = store.loadSession("s1");
        assertEquals(List.of(new Message.UserMessage("hi"),
                new Message.AssistantMessage("hello", List.of())), messages);
    }

    private static TraceEvent messageEvent(Message message) {
        return new TraceEvent(EventKinds.MESSAGE_APPENDED, 1L, "r1", "s1", "n",
                Map.of("message", MessageCodec.toMap(message)));
    }
}
