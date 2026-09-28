package tech.forethought.brick.store.jsonl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.event.EventListener;
import tech.forethought.brick.core.event.TraceEvent;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.MessageCodec;
import tech.forethought.brick.core.spi.StateStore;
import tech.forethought.brick.core.util.Json;

/**
 * JSONL session store: one append-only file per session
 * ({@code <dir>/<sessionId>.jsonl}). Durable events are appended as JSON
 * lines; transient events (token-delta) are never written. The read side
 * ({@link StateStore}) replays message-appended events.
 *
 * <p>Thread-safe: writes are synchronized. Persistence failures are logged
 * to stderr and never break a run. Instances are assembled programmatically
 * (the directory is configuration), not through ServiceLoader.
 */
public final class JsonlEventStore implements EventListener, StateStore {

    private final Path dir;

    public JsonlEventStore(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create session dir " + dir, e);
        }
    }

    @Override
    public synchronized void onEvent(TraceEvent event) {
        if (EventKinds.TOKEN_DELTA.equals(event.kind())) {
            return;
        }
        try {
            Files.writeString(fileFor(event.sessionId()), toJson(event) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("brick: store-jsonl failed to persist " + event.kind() + ": "
                    + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Message> loadSession(String sessionId) {
        var file = fileFor(sessionId);
        if (!Files.exists(file)) {
            return List.of();
        }
        var messages = new ArrayList<Message>();
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.map(line -> (Map<String, Object>) Json.read(line))
                    .filter(event -> EventKinds.MESSAGE_APPENDED.equals(event.get("kind")))
                    .forEach(event -> messages.add(MessageCodec.fromMap(
                            (Map<String, Object>) ((Map<String, Object>) event.get("payload"))
                                    .get("message"))));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read session file " + file, e);
        }
        return List.copyOf(messages);
    }

    private Path fileFor(String sessionId) {
        return dir.resolve(sessionId + ".jsonl");
    }

    private static String toJson(TraceEvent event) {
        var map = new LinkedHashMap<String, Object>();
        map.put("kind", event.kind());
        map.put("timestamp", event.timestamp());
        map.put("runId", event.runId());
        map.put("sessionId", event.sessionId());
        if (event.nodeId() != null) {
            map.put("nodeId", event.nodeId());
        }
        map.put("payload", event.payload());
        return Json.write(map);
    }
}
