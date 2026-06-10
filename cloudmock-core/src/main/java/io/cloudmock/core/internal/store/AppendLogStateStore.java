package io.cloudmock.core.internal.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudmock.core.exception.CloudMockStateException;
import io.cloudmock.core.spi.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent {@link StateStore} that records each mutation as a single appended record in an
 * append-only log, rather than rewriting the whole document on every change.
 *
 * <p>This is the efficient embedded backend from issue 0047. A {@code put}/{@code delete}/{@code
 * clear}/{@code clearAll} appends one compact JSON line to {@code {storeDir}/cloudmock-state.log}
 * and returns once it has been written and flushed to the OS. A single write therefore costs the
 * size of the change, not the size of the store, and a burst of M writes is O(M) bytes rather than
 * O(M²). The log is replayed on construction so state survives a restart.
 *
 * <p><strong>Compaction:</strong> the log grows with every mutation, so once it holds substantially
 * more records than live entries it is rewritten as a minimal snapshot (one {@code put} per live key)
 * via an atomic temp-file rename. This bounds the on-disk size and replay time without making the
 * common write path O(n). A compaction failure never fails the caller's write nor breaks the store:
 * the live writer is reopened and the existing log is left intact.
 *
 * <p><strong>Type fidelity:</strong> each record is serialised through {@link StateStoreMapper} with
 * default typing, and the value rides in a field declared {@code Object}, so a value stored as a
 * {@code Foo} reads back as a {@code Foo} after a restart — matching {@link JsonFileStateStore} and
 * the in-memory store.
 *
 * <p><strong>Migration:</strong> on first run against a directory that holds a legacy
 * {@code cloudmock-state.json} (the {@link JsonFileStateStore} format) but no log yet, the legacy
 * entries are imported into the log so upgrading the default backend does not drop existing state.
 *
 * <p>Thread-safe: reads hit a {@link ConcurrentHashMap} lock-free; every mutation (log append plus
 * the in-memory update) is serialised through {@code writeLock} so the log and the map never diverge.
 */
public final class AppendLogStateStore implements StateStore {

    private static final Logger log = LoggerFactory.getLogger(AppendLogStateStore.class);
    private static final String LOG_FILE_NAME = "cloudmock-state.log";
    private static final String LEGACY_FILE_NAME = "cloudmock-state.json";

    /** Don't compact tiny logs; compaction I/O isn't worth it until the log is sizeable. */
    private static final int COMPACTION_MIN_RECORDS = 1_000;
    /** Compact once the log holds this many times more records than there are live entries. */
    private static final int COMPACTION_GROWTH_FACTOR = 2;

    private static final ObjectMapper MAPPER = StateStoreMapper.create();
    private static final JavaType LEGACY_MAP_TYPE =
            MAPPER.getTypeFactory().constructMapType(TreeMap.class, String.class, Object.class);

    private final Path logFile;
    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    private BufferedWriter writer;
    private int recordCount;

    public AppendLogStateStore(Path storeDir) {
        this.logFile = storeDir.resolve(LOG_FILE_NAME);
        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            throw new CloudMockStateException(
                    "CloudMock failed to create state directory " + storeDir, e);
        }
        boolean freshLog = !Files.exists(logFile);
        replay();
        openWriter();
        if (freshLog) {
            migrateLegacyJsonIfPresent(storeDir);
        }
    }

    @Override
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        synchronized (writeLock) {
            append(new LogRecord("put", key, value, null));
            data.put(key, value);
            maybeCompact();
        }
    }

    @Override
    public Object get(String key) {
        return data.get(key);
    }

    @Override
    public List<String> list(String prefix) {
        return StateStoreSupport.keysWithPrefix(data.keySet(), prefix);
    }

    @Override
    public void delete(String key) {
        synchronized (writeLock) {
            if (data.remove(key) == null) {
                return; // No-op for a missing key; don't grow the log with a useless tombstone.
            }
            append(new LogRecord("delete", key, null, null));
            maybeCompact();
        }
    }

    @Override
    public void clear(String prefix) {
        synchronized (writeLock) {
            if (!StateStoreSupport.removeKeysWithPrefix(data, prefix)) {
                return; // Nothing matched; skip the record.
            }
            append(new LogRecord("clear", null, null, prefix));
            maybeCompact();
        }
    }

    @Override
    public void clearAll() {
        synchronized (writeLock) {
            data.clear();
            append(new LogRecord("clearAll", null, null, null));
            maybeCompact();
        }
    }

    private void append(LogRecord record) {
        try {
            writer.write(serialize(record));
            writer.write('\n');
            writer.flush();
            recordCount++;
        } catch (IOException e) {
            throw new CloudMockStateException("CloudMock failed to persist state to " + logFile, e);
        }
    }

    /**
     * Serialises a record in one pass. The value rides in {@link LogRecord#val()}, declared
     * {@code Object}, so default typing embeds its concrete type (it would be omitted for a
     * {@code final} runtime type if the declared type were that type) — exactly as the {@code
     * Map<String, Object>} value position does in {@link JsonFileStateStore}.
     */
    private static String serialize(LogRecord record) throws IOException {
        return MAPPER.writeValueAsString(record);
    }

    private void maybeCompact() {
        if (recordCount > COMPACTION_MIN_RECORDS
                && recordCount > (long) data.size() * COMPACTION_GROWTH_FACTOR) {
            try {
                compact();
            } catch (IOException | RuntimeException e) {
                // Compaction is an optimisation: the live data and the existing log are intact, so a
                // failure here must not fail the caller's write. The writer is reopened by compact()'s
                // finally, so the store stays usable; just log and carry on with the current log.
                log.warn("CloudMock failed to compact state log {}, continuing without compaction: {}",
                        logFile, e.getMessage());
            }
        }
    }

    /** Rewrites the log as one {@code put} per live key, then swaps it in atomically. */
    private void compact() throws IOException {
        // Close the live writer only for the swap, and always reopen it in the finally — so a failed
        // snapshot/move leaves the store writable against the still-intact original log. (Only if the
        // reopen itself fails does the store stop accepting writes, surfacing loudly on the next call;
        // that means the log file can no longer be opened at all, a genuine disk failure.)
        writer.close();
        try {
            StateStoreSupport.atomicReplace(logFile, this::writeSnapshot);
            recordCount = data.size();
        } finally {
            openWriter();
        }
    }

    private void writeSnapshot(Path tmp) throws IOException {
        try (BufferedWriter snapshot = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            // TreeMap gives deterministic, diff-friendly key order in the snapshot.
            for (var entry : new TreeMap<>(data).entrySet()) {
                snapshot.write(serialize(new LogRecord("put", entry.getKey(), entry.getValue(), null)));
                snapshot.write('\n');
            }
        }
    }

    private void openWriter() {
        try {
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new CloudMockStateException("CloudMock failed to open state log " + logFile, e);
        }
    }

    private void replay() {
        if (!Files.exists(logFile)) {
            return;
        }
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    apply(MAPPER.readValue(line, LogRecord.class));
                    count++;
                } catch (IOException | RuntimeException parse) {
                    // A truncated final record (process killed mid-append) ends replay; every
                    // fully-written record before it stands. Catching RuntimeException too means a
                    // single malformed record never blocks startup.
                    log.warn("CloudMock state log {} has an unreadable record, stopping replay: {}",
                            logFile, parse.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            // An unreadable log should not block startup; begin with an empty store.
            log.warn("CloudMock state log {} is unreadable, starting with empty store: {}",
                    logFile, e.getMessage());
            data.clear();
            count = 0;
        }
        recordCount = count;
    }

    private void apply(LogRecord record) {
        switch (record.op()) {
            case "put" -> {
                if (record.key() == null || record.val() == null) {
                    log.warn("CloudMock state log {} has a put record without a key/value, skipping",
                            logFile);
                    return;
                }
                data.put(record.key(), record.val());
            }
            case "delete" -> {
                if (record.key() != null) {
                    data.remove(record.key());
                }
            }
            case "clear" -> {
                if (record.prefix() != null) {
                    StateStoreSupport.removeKeysWithPrefix(data, record.prefix());
                }
            }
            case "clearAll" -> data.clear();
            default -> log.warn("CloudMock state log {} has an unknown op '{}', skipping",
                    logFile, record.op());
        }
    }

    private void migrateLegacyJsonIfPresent(Path storeDir) {
        Path legacy = storeDir.resolve(LEGACY_FILE_NAME);
        if (!Files.exists(legacy)) {
            return;
        }
        try {
            Map<String, Object> loaded = MAPPER.readValue(legacy.toFile(), LEGACY_MAP_TYPE);
            loaded.forEach(this::put);
            // Rename the legacy file so it is not re-imported if the log is later deleted.
            Files.move(legacy, legacy.resolveSibling(LEGACY_FILE_NAME + ".migrated"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("CloudMock migrated {} entries from legacy state file {} into {}",
                    loaded.size(), legacy, logFile);
        } catch (IOException | RuntimeException e) {
            log.warn("CloudMock failed to migrate legacy state file {}, leaving it in place: {}",
                    legacy, e.getMessage());
        }
    }

    /**
     * One line of the append log. {@code val} is declared {@code Object} so default typing carries
     * the value's concrete type; unused fields are omitted from the JSON.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LogRecord(String op, String key, Object val, String prefix) {}
}
