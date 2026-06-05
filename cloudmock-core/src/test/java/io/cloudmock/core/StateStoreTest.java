package io.cloudmock.core;

import io.cloudmock.core.exception.CloudMockStateException;
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
