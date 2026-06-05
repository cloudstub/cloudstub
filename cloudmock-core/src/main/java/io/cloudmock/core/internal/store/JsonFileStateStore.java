package io.cloudmock.core.internal.store;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.cloudmock.core.exception.CloudMockStateException;
import io.cloudmock.core.spi.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent {@link StateStore} that serialises state to a JSON file on every mutation.
 *
 * <p>Reads the existing file on construction (if present) so state survives a CloudMock restart.
 * Writes are flushed synchronously — {@code put} returns only once the value is on disk — using an
 * atomic temp-file rename to {@code {storeDir}/cloudmock-state.json} to prevent partial writes.
 *
 * <p><strong>Type fidelity:</strong> the mapper records each value's concrete type, so a value
 * stored as a {@code Foo} is read back as a {@code Foo} after a restart — not as a generic map.
 * This matches the in-memory store's behaviour. Stored value types must be on the classpath when
 * the store is reloaded. Default typing is safe here because the store only ever reads back its own
 * locally-written file, never untrusted input.
 *
 * <p>Thread-safe: mutations are serialised through {@code writeLock}.
 */
public final class JsonFileStateStore implements StateStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileStateStore.class);
    private static final String STORE_FILE_NAME = "cloudmock-state.json";

    private static final ObjectMapper MAPPER = createMapper();
    private static final JavaType MAP_TYPE =
            MAPPER.getTypeFactory().constructMapType(TreeMap.class, String.class, Object.class);

    private final Path storeFile;
    private final ConcurrentHashMap<String, Object> data;
    private final Object writeLock = new Object();

    public JsonFileStateStore(Path storeDir) {
        this.storeFile = storeDir.resolve(STORE_FILE_NAME);
        this.data = loadFromDisk();
    }

    private static ObjectMapper createMapper() {
        ObjectMapper m = new ObjectMapper();
        // Embed concrete type info so values round-trip to their original Java types on reload.
        m.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        return m;
    }

    @Override
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        data.put(key, value);
        flush();
    }

    @Override
    public Object get(String key) {
        return data.get(key);
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        for (String key : data.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    @Override
    public void delete(String key) {
        data.remove(key);
        flush();
    }

    @Override
    public void clear(String prefix) {
        data.keySet().removeIf(key -> key.startsWith(prefix));
        flush();
    }

    @Override
    public void clearAll() {
        data.clear();
        flush();
    }

    private ConcurrentHashMap<String, Object> loadFromDisk() {
        if (!Files.exists(storeFile)) {
            return new ConcurrentHashMap<>();
        }
        try {
            Map<String, Object> loaded = MAPPER.readValue(storeFile.toFile(), MAP_TYPE);
            return new ConcurrentHashMap<>(loaded);
        } catch (IOException e) {
            // A corrupt or unreadable file should not prevent startup; begin with an empty store.
            log.warn("CloudMock state file at {} is unreadable, starting with empty store: {}",
                    storeFile, e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    private void flush() {
        synchronized (writeLock) {
            try {
                Files.createDirectories(storeFile.getParent());
                Path tmp = storeFile.resolveSibling(STORE_FILE_NAME + ".tmp");
                // Snapshot under the lock; TreeMap gives deterministic, diff-friendly key order.
                MAPPER.writerFor(MAP_TYPE).writeValue(tmp.toFile(), new TreeMap<>(data));
                Files.move(tmp, storeFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // Persistence is a hard requirement — surface the failure instead of swallowing it.
                throw new CloudMockStateException(
                        "CloudMock failed to persist state to " + storeFile, e);
            }
        }
    }
}
