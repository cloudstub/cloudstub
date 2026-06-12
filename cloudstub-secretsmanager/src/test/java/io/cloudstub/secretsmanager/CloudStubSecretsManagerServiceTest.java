package io.cloudstub.secretsmanager;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;

/**
 * Integration tests for {@link CloudStubSecretsManagerService}.
 *
 * <p>Each test drives a real {@code SecretsManagerClient} (AWS SDK v2) against a live {@code
 * CloudStub} instance. Assertions verify that responses are well-formed enough for the SDK to parse
 * — not that AWS service semantics are reproduced. Stage 1 contract mocking.
 *
 * <p>This test class is the reference example for the JSON/X-Amz-Target module pattern.
 */
class CloudStubSecretsManagerServiceTest {

    static CloudStub cloudMock;
    static SecretsManagerClient client;

    static final String SECRET_NAME = "my-test-secret";

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubSecretsManagerService());
        cloudMock.start();
        client =
                SecretsManagerClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
    }

    @AfterAll
    static void stop() {
        client.close();
        cloudMock.stop();
    }

    @Test
    void createSecretReturnsArnAndName() {
        CreateSecretResponse response =
                client.createSecret(b -> b.name(SECRET_NAME).secretString("value"));
        assertNotNull(response.arn());
        assertTrue(response.arn().contains(SECRET_NAME));
        assertEquals(SECRET_NAME, response.name());
        assertNotNull(response.versionId());
    }

    @Test
    void getSecretValueReturnsSecretString() {
        GetSecretValueResponse response = client.getSecretValue(b -> b.secretId(SECRET_NAME));
        assertNotNull(response.arn());
        assertTrue(response.arn().contains(SECRET_NAME));
        assertEquals(SECRET_NAME, response.name());
        assertNotNull(response.secretString());
        assertFalse(response.secretString().isBlank());
    }

    @Test
    void putSecretValueReturnsArnAndVersionId() {
        PutSecretValueResponse response =
                client.putSecretValue(b -> b.secretId(SECRET_NAME).secretString("new-value"));
        assertNotNull(response.arn());
        assertNotNull(response.versionId());
    }

    @Test
    void deleteSecretReturnsArnAndName() {
        DeleteSecretResponse response = client.deleteSecret(b -> b.secretId(SECRET_NAME));
        assertNotNull(response.arn());
        assertNotNull(response.name());
    }

    @Test
    void listSecretsReturnsNonEmptyList() {
        assertFalse(client.listSecrets().secretList().isEmpty());
    }
}
