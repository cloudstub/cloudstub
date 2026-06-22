package io.cloudstub.example;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.example.service.ObjectStore;
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class ObjectStoreIntegrationTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

    @Autowired ObjectStore store;

    @Test
    void putThenGetReturnsStoredBody() {
        store.createBucket();
        store.put("greeting.txt", "hello world");
        assertEquals("hello world", store.get("greeting.txt"));
    }

    @Test
    void listReflectsStoredKey() {
        store.createBucket();
        store.put("invoice.pdf", "payload");
        assertTrue(store.list().contains("invoice.pdf"));
    }
}
