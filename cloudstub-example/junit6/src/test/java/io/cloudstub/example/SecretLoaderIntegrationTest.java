package io.cloudstub.example;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.example.service.SecretLoader;
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class SecretLoaderIntegrationTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

    @Autowired SecretLoader loader;

    @Test
    void storeReturnsArnContainingSecretName() {
        String arn = loader.store("api-key", "abc123");
        assertNotNull(arn);
        assertTrue(arn.contains("api-key"));
    }

    @Test
    void storeThenLoadReturnsStoredValue() {
        loader.store("db-password", "s3cr3t");
        assertEquals("s3cr3t", loader.load("db-password"));
    }
}
