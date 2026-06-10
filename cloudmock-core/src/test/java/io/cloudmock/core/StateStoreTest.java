package io.cloudmock.core;

import io.cloudmock.core.exception.CloudMockStateException;
import io.cloudmock.core.internal.store.AppendLogStateStore;
import io.cloudmock.core.internal.store.InMemoryStateStore;
import io.cloudmock.core.internal.store.JsonFileStateStore;
import io.cloudmock.core.spi.StateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StateStoreTest {

    // --- InMemoryStateStore ---

    @Test
    void putAndGet() {
        StateStore store = new InMemoryStateStore();
        store.put("sqs/queues/q1", "payload");
        assertEquals("payload", store.get("sqs/queues/q1"));
    }

    @Test
    void getMissingKeyReturnsNull() {
        StateStore store = new InMemoryStateStore();
        assertNull(store.get("does/not/exist"));
    }

    @Test
    void putOverwritesExistingKey() {
        StateStore store = new InMemoryStateStore();
        store.put("k", "first");
        store.put("k", "second");
        assertEquals("second", store.get("k"));
    }

    @Test
    void listReturnsKeysMatchingPrefix() {
        StateStore store = new InMemoryStateStore();
        store.put("sqs/queues/a", 1);
        store.put("sqs/queues/b", 2);
        store.put("s3/buckets/x", 3);

        List<String> sqsKeys = store.list("sqs/");
        assertEquals(List.of("sqs/queues/a", "sqs/queues/b"), sqsKeys);
    }

    @Test
    void listReturnsSortedKeys() {
        StateStore store = new InMemoryStateStore();
        store.put("sqs/queues/z", 1);
        store.put("sqs/queues/a", 2);
        store.put("sqs/queues/m", 3);

        assertEquals(List.of("sqs/queues/a", "sqs/queues/m", "sqs/queues/z"), store.list("sqs/"));
    }

    @Test
    void listReturnsEmptyWhenNothingMatchesPrefix() {
        StateStore store = new InMemoryStateStore();
        store.put("sqs/queues/a", 1);
        assertTrue(store.list("dynamodb/").isEmpty());
    }

    @Test
    void deleteRemovesKey() {
        StateStore store = new InMemoryStateStore();
        store.put("k", "v");
        store.delete("k");
        assertNull(store.get("k"));
    }

    @Test
    void deleteNonExistentKeyIsNoOp() {
        StateStore store = new InMemoryStateStore();
        assertDoesNotThrow(() -> store.delete("ghost"));
    }

    @Test
    void clearRemovesAllKeysWithPrefix() {
        StateStore store = new InMemoryStateStore();
        store.put("sqs/queues/a", 1);
        store.put("sqs/queues/b", 2);
        store.put("s3/buckets/x", 3);

        store.clear("sqs/");

        assertTrue(store.list("sqs/").isEmpty());
        assertEquals(1, store.list("s3/").size());
    }

    @Test
    void clearAllRemovesEverything() {
        StateStore store = new InMemoryStateStore();
        store.put("sqs/queues/a", 1);
        store.put("s3/buckets/x", 2);

        store.clearAll();

        assertTrue(store.list("sqs/").isEmpty());
        assertTrue(store.list("s3/").isEmpty());
    }

    // --- JsonFileStateStore ---

    @Test
    void jsonStoreRoundTripsData(@TempDir Path tmp) {
        StateStore store = new JsonFileStateStore(tmp);
        store.put("sqs/queues/my-queue", "data");

        StateStore reloaded = new JsonFileStateStore(tmp);
        assertEquals("data", reloaded.get("sqs/queues/my-queue"));
    }

    @Test
    void jsonStorePersistsAcrossInstances(@TempDir Path tmp) {
        new JsonFileStateStore(tmp).put("key", "value");
        assertEquals("value", new JsonFileStateStore(tmp).get("key"));
    }

    @Test
    void jsonStoreClearAllPersists(@TempDir Path tmp) {
        StateStore store = new JsonFileStateStore(tmp);
        store.put("k", "v");
        store.clearAll();

        StateStore reloaded = new JsonFileStateStore(tmp);
        assertNull(reloaded.get("k"));
    }

    @Test
    void jsonStoreStartsEmptyWhenFileAbsent(@TempDir Path tmp) {
        StateStore store = new JsonFileStateStore(tmp);
        assertTrue(store.list("").isEmpty());
    }

    @Test
    void jsonStorePreservesValueTypeAcrossRestart(@TempDir Path tmp) {
        StoredMessage original = new StoredMessage("hello body", "receipt-42");
        new JsonFileStateStore(tmp).put("sqs/queues/q/messages/1", original);

        Object reloaded = new JsonFileStateStore(tmp).get("sqs/queues/q/messages/1");

        // Must come back as the original concrete type, not a generic LinkedHashMap.
        assertInstanceOf(StoredMessage.class, reloaded);
        StoredMessage msg = (StoredMessage) reloaded;
        assertEquals("hello body", msg.body);
        assertEquals("receipt-42", msg.receiptHandle);
    }

    @Test
    void jsonStorePreservesCollectionTypeAcrossRestart(@TempDir Path tmp) {
        new JsonFileStateStore(tmp).put("sqs/queues/q/names", new ArrayList<>(List.of("a", "b")));

        Object reloaded = new JsonFileStateStore(tmp).get("sqs/queues/q/names");

        assertInstanceOf(List.class, reloaded);
        assertEquals(List.of("a", "b"), reloaded);
    }

    @Test
    void jsonStoreSurfacesWriteFailure(@TempDir Path tmp) throws Exception {
        // Make the store directory a regular file so directory creation during flush fails.
        Path notADir = tmp.resolve("blocker");
        Files.createFile(notADir);
        StateStore store = new JsonFileStateStore(notADir.resolve("nested"));

        assertThrows(CloudMockStateException.class, () -> store.put("k", "v"));
    }

    @Test
    void putRejectsNullValue() {
        StateStore store = new InMemoryStateStore();
        assertThrows(NullPointerException.class, () -> store.put("k", null));
    }

    @Test
    void putRejectsNullKey() {
        StateStore store = new InMemoryStateStore();
        assertThrows(NullPointerException.class, () -> store.put(null, "v"));
    }

    // --- AppendLogStateStore (issue 0047) ---

    @Test
    void appendLogRoundTripsData(@TempDir Path tmp) {
        StateStore store = new AppendLogStateStore(tmp);
        store.put("sqs/queues/my-queue", "data");

        StateStore reloaded = new AppendLogStateStore(tmp);
        assertEquals("data", reloaded.get("sqs/queues/my-queue"));
    }

    @Test
    void appendLogPersistsAcrossInstances(@TempDir Path tmp) {
        new AppendLogStateStore(tmp).put("key", "value");
        assertEquals("value", new AppendLogStateStore(tmp).get("key"));
    }

    @Test
    void appendLogReplaysOverwrites(@TempDir Path tmp) {
        StateStore store = new AppendLogStateStore(tmp);
        store.put("k", "first");
        store.put("k", "second");

        assertEquals("second", new AppendLogStateStore(tmp).get("k"));
    }

    @Test
    void appendLogReplaysDeletes(@TempDir Path tmp) {
        StateStore store = new AppendLogStateStore(tmp);
        store.put("k", "v");
        store.delete("k");

        assertNull(new AppendLogStateStore(tmp).get("k"));
    }

    @Test
    void appendLogReplaysPrefixClear(@TempDir Path tmp) {
        StateStore store = new AppendLogStateStore(tmp);
        store.put("sqs/a", 1);
        store.put("sqs/b", 2);
        store.put("s3/x", 3);
        store.clear("sqs/");

        StateStore reloaded = new AppendLogStateStore(tmp);
        assertTrue(reloaded.list("sqs/").isEmpty());
        assertEquals(1, reloaded.list("s3/").size());
    }

    @Test
    void appendLogClearAllPersists(@TempDir Path tmp) {
        StateStore store = new AppendLogStateStore(tmp);
        store.put("k", "v");
        store.clearAll();

        assertNull(new AppendLogStateStore(tmp).get("k"));
    }

    @Test
    void appendLogStartsEmptyWhenFileAbsent(@TempDir Path tmp) {
        StateStore store = new AppendLogStateStore(tmp);
        assertTrue(store.list("").isEmpty());
    }

    @Test
    void appendLogPreservesValueTypeAcrossRestart(@TempDir Path tmp) {
        StoredMessage original = new StoredMessage("hello body", "receipt-42");
        new AppendLogStateStore(tmp).put("sqs/queues/q/messages/1", original);

        Object reloaded = new AppendLogStateStore(tmp).get("sqs/queues/q/messages/1");

        assertInstanceOf(StoredMessage.class, reloaded);
        StoredMessage msg = (StoredMessage) reloaded;
        assertEquals("hello body", msg.body);
        assertEquals("receipt-42", msg.receiptHandle);
    }

    @Test
    void appendLogPreservesCollectionTypeAcrossRestart(@TempDir Path tmp) {
        new AppendLogStateStore(tmp).put("sqs/queues/q/names", new ArrayList<>(List.of("a", "b")));

        Object reloaded = new AppendLogStateStore(tmp).get("sqs/queues/q/names");

        assertInstanceOf(List.class, reloaded);
        assertEquals(List.of("a", "b"), reloaded);
    }

    @Test
    void appendLogSurfacesWriteFailure(@TempDir Path tmp) throws Exception {
        // Make the store directory path a regular file so directory creation fails.
        Path notADir = tmp.resolve("blocker");
        Files.createFile(notADir);

        assertThrows(CloudMockStateException.class,
                () -> new AppendLogStateStore(notADir.resolve("nested")));
    }

    @Test
    void appendLogCompactsRepeatedWritesToSameKey(@TempDir Path tmp) throws Exception {
        StateStore store = new AppendLogStateStore(tmp);
        // A burst of writes to one key: the live store holds a single entry the whole time, so
        // compaction must keep the log small rather than letting it grow with every write.
        for (int i = 0; i < 5_000; i++) {
            store.put("sqs/queues/q/messages/1", "payload-" + i);
        }

        Path logFile = tmp.resolve("cloudmock-state.log");
        long records = countRecords(logFile);
        // Compaction bounds the log near its threshold, independent of how many writes happened:
        // 5000 appends to one key must not leave anything close to 5000 records on disk.
        assertTrue(records < 1_100, "expected a compacted log, but found " + records + " records");

        assertEquals("payload-4999", new AppendLogStateStore(tmp).get("sqs/queues/q/messages/1"));
    }

    @Test
    void appendLogCompactionShrinksAGrownLogAndPreservesKeys(@TempDir Path tmp) throws Exception {
        StateStore store = new AppendLogStateStore(tmp);
        Path logFile = tmp.resolve("cloudmock-state.log");

        // 500 live keys, then rewrite them repeatedly so the record count outgrows the live set by
        // more than the growth factor and crosses the min-records floor — this is what triggers
        // compaction (distinct-key inserts alone never do, since records track live entries).
        int keys = 500;
        for (int i = 0; i < keys; i++) {
            store.put("k/" + i, "v0");
        }
        for (int round = 1; round <= 5; round++) {
            for (int i = 0; i < keys; i++) {
                store.put("k/" + i, "v" + round);
            }
        }

        // 3000 appends, but compaction rewrote the log back down toward the live size; the log is
        // bounded near the compaction floor (COMPACTION_MIN_RECORDS = 1000), far below 3000.
        long records = countRecords(logFile);
        assertTrue(records <= 1_000,
                "expected compaction to bound the log, found " + records + " records");

        StateStore reloaded = new AppendLogStateStore(tmp);
        assertEquals(keys, reloaded.list("k/").size());
        assertEquals("v5", reloaded.get("k/0"));
        assertEquals("v5", reloaded.get("k/499"));
    }

    @Test
    void appendLogReplaysManyDistinctKeys(@TempDir Path tmp) {
        StateStore store = new AppendLogStateStore(tmp);
        int n = 3_000;
        for (int i = 0; i < n; i++) {
            store.put("sqs/queues/q/messages/" + i, "body-" + i);
        }

        StateStore reloaded = new AppendLogStateStore(tmp);
        assertEquals(n, reloaded.list("sqs/").size());
        assertEquals("body-0", reloaded.get("sqs/queues/q/messages/0"));
        assertEquals("body-2999", reloaded.get("sqs/queues/q/messages/2999"));
    }

    @Test
    void appendLogToleratesTruncatedTrailingRecord(@TempDir Path tmp) throws Exception {
        StateStore store = new AppendLogStateStore(tmp);
        store.put("k1", "v1");
        store.put("k2", "v2");

        // Simulate a crash mid-append by appending a half-written record (invalid JSON).
        Path logFile = tmp.resolve("cloudmock-state.log");
        Files.writeString(logFile, "{\"op\":\"put\",\"key\":\"k3\",\"va",
                java.nio.file.StandardOpenOption.APPEND);

        StateStore reloaded = new AppendLogStateStore(tmp);
        assertEquals("v1", reloaded.get("k1"));
        assertEquals("v2", reloaded.get("k2"));
        assertNull(reloaded.get("k3"));
    }

    @Test
    void appendLogToleratesValidJsonRecordMissingFields(@TempDir Path tmp) throws Exception {
        StateStore store = new AppendLogStateStore(tmp);
        store.put("k1", "v1");

        // A structurally-valid JSON line missing its "key"/"val" must not abort startup — it is
        // skipped, mirroring JsonFileStateStore's "corrupt file starts empty rather than failing".
        Path logFile = tmp.resolve("cloudmock-state.log");
        Files.writeString(logFile, "{\"op\":\"put\",\"key\":\"k2\"}\n",
                java.nio.file.StandardOpenOption.APPEND);
        store.put("k3", "v3");

        StateStore reloaded = new AppendLogStateStore(tmp);
        assertEquals("v1", reloaded.get("k1"));
        assertNull(reloaded.get("k2"));
        assertEquals("v3", reloaded.get("k3"));
    }

    @Test
    void appendLogMigratesLegacyJsonStoreOnFirstRun(@TempDir Path tmp) {
        // Seed a legacy JSON store, then open the directory with the new append-log backend.
        StateStore legacy = new JsonFileStateStore(tmp);
        legacy.put("sqs/queues/q/messages/1", new StoredMessage("hi", "r-1"));
        legacy.put("secrets/api-key", "s3cr3t");

        StateStore migrated = new AppendLogStateStore(tmp);
        assertEquals("s3cr3t", migrated.get("secrets/api-key"));
        Object msg = migrated.get("sqs/queues/q/messages/1");
        assertInstanceOf(StoredMessage.class, msg);
        assertEquals("hi", ((StoredMessage) msg).body);

        // The legacy file is renamed so it cannot be re-imported, even if the log is later deleted.
        assertFalse(Files.exists(tmp.resolve("cloudmock-state.json")));
        assertTrue(Files.exists(tmp.resolve("cloudmock-state.json.migrated")));

        // Migration is one-time: the imported state survives a restart, and a second open neither
        // re-imports nor loses anything.
        StateStore reopened = new AppendLogStateStore(tmp);
        assertEquals("s3cr3t", reopened.get("secrets/api-key"));
        assertEquals(2, reopened.list("").size());
    }

    private static long countRecords(Path logFile) throws java.io.IOException {
        try (var lines = Files.lines(logFile)) {
            return lines.filter(line -> !line.isEmpty()).count();
        }
    }

    /** Simple POJO used to verify type fidelity across a persistent-store restart. */
    public static final class StoredMessage {
        public String body;
        public String receiptHandle;

        public StoredMessage() {}

        public StoredMessage(String body, String receiptHandle) {
            this.body = body;
            this.receiptHandle = receiptHandle;
        }
    }

    // --- CloudMock integration ---

    @Test
    void cloudMockExposesStateStoreAfterStart() {
        CloudMock cloudMock = new CloudMock();
        cloudMock.start();
        try {
            assertNotNull(cloudMock.stateStore());
            cloudMock.stateStore().put("test/key", "value");
            assertEquals("value", cloudMock.stateStore().get("test/key"));
        } finally {
            cloudMock.stop();
        }
    }

    @Test
    void cloudMockWithStoreDirectoryUsesPersistentStore(@TempDir Path tmp) {
        CloudMock first = new CloudMock().withStoreDirectory(tmp);
        first.start();
        first.stateStore().put("persisted/key", "hello");
        first.stop();

        CloudMock second = new CloudMock().withStoreDirectory(tmp);
        second.start();
        try {
            assertEquals("hello", second.stateStore().get("persisted/key"));
        } finally {
            second.stop();
        }
    }

    @Test
    void stateStoreThrowsWhenNotStarted() {
        CloudMock cloudMock = new CloudMock();
        assertThrows(Exception.class, cloudMock::stateStore);
    }
}
