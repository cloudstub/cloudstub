package io.cloudmock.example;

import io.cloudmock.example.service.SecretLoader;
import io.cloudmock.junit.CloudMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class SecretLoaderIntegrationTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension();

    @Autowired SecretLoader loader;

    @Test
    void storeReturnsArnContainingSecretName() {
        String arn = loader.store("api-key", "abc123");
        assertNotNull(arn);
        assertTrue(arn.contains("api-key"));
    }

    @Test
    void loadReturnsNonBlankValue() {
        String value = loader.load("api-key");
        assertFalse(value.isBlank());
    }
}
