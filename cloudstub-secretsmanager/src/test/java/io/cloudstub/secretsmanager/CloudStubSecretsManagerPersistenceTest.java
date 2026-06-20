package io.cloudstub.secretsmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Verifies that a secret written through the AWS SDK survives a full CloudStub restart when a
 * persistent store directory is configured. Exercises the structured ({@code Map}) state value
 * round-tripping through the persistent backend.
 */
class CloudStubSecretsManagerPersistenceTest {

    @Test
    void secretSurvivesRestart(@TempDir Path storeDir) {
        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubSecretsManagerService())) {
            cloudMock.start();
            try (SecretsManagerClient client = client(cloudMock.port())) {
                client.createSecret(b -> b.name("durable").secretString("persisted"));
            }
        }

        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubSecretsManagerService())) {
            cloudMock.start();
            try (SecretsManagerClient client = client(cloudMock.port())) {
                assertEquals(
                        "persisted",
                        client.getSecretValue(b -> b.secretId("durable")).secretString(),
                        "secret must survive a restart");
            }
        }
    }

    private static SecretsManagerClient client(int port) {
        return SecretsManagerClient.builder()
                .endpointOverride(URI.create("http://localhost:" + port))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }
}
