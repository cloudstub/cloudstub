package io.cloudstub.secretsmanager;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * Tests for {@link CloudStubSecretsManagerService}.
 *
 * <p>Each test drives a real {@code SecretsManagerClient} (AWS SDK v2) against a live {@code
 * CloudStub} instance. The core secret operations are state-backed, so assertions verify that a
 * value written by one call is read back by a later one — not merely that the SDK can parse the
 * response.
 */
class CloudStubSecretsManagerServiceTest {

    static CloudStub cloudMock;
    static SecretsManagerClient client;

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

    @BeforeEach
    void reset() {
        cloudMock.stateStore().clearAll();
    }

    @Test
    void createThenGetReturnsStoredValue() {
        CreateSecretResponse created =
                client.createSecret(b -> b.name("db-password").secretString("s3cr3t"));
        assertTrue(created.arn().contains("db-password"));
        assertEquals("db-password", created.name());
        assertNotNull(created.versionId());

        GetSecretValueResponse got = client.getSecretValue(b -> b.secretId("db-password"));
        assertEquals("s3cr3t", got.secretString());
        assertEquals("db-password", got.name());
        assertEquals(created.arn(), got.arn());
    }

    @Test
    void getByArnResolvesToSameSecret() {
        CreateSecretResponse created =
                client.createSecret(b -> b.name("api-key").secretString("abc123"));
        GetSecretValueResponse got = client.getSecretValue(b -> b.secretId(created.arn()));
        assertEquals("abc123", got.secretString());
    }

    @Test
    void putSecretValueUpdatesStoredValue() {
        client.createSecret(b -> b.name("rotating").secretString("v1"));
        PutSecretValueResponse put =
                client.putSecretValue(b -> b.secretId("rotating").secretString("v2"));
        assertNotNull(put.versionId());
        assertEquals("v2", client.getSecretValue(b -> b.secretId("rotating")).secretString());
    }

    @Test
    void updateSecretChangesDescription() {
        client.createSecret(b -> b.name("with-desc").secretString("x").description("old"));
        client.updateSecret(b -> b.secretId("with-desc").description("new"));
        DescribeSecretResponse described = client.describeSecret(b -> b.secretId("with-desc"));
        assertEquals("new", described.description());
        assertEquals("with-desc", described.name());
    }

    @Test
    void listSecretsReturnsCreatedSecrets() {
        client.createSecret(b -> b.name("one").secretString("a"));
        client.createSecret(b -> b.name("two").secretString("b"));
        var names = client.listSecrets().secretList().stream().map(s -> s.name()).toList();
        assertTrue(names.contains("one"));
        assertTrue(names.contains("two"));
    }

    @Test
    void deleteSecretRemovesIt() {
        client.createSecret(b -> b.name("temp").secretString("x"));
        client.deleteSecret(b -> b.secretId("temp"));
        assertThrows(
                ResourceNotFoundException.class,
                () -> client.getSecretValue(b -> b.secretId("temp")));
    }

    @Test
    void getMissingSecretThrowsResourceNotFound() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> client.getSecretValue(b -> b.secretId("does-not-exist")));
    }

    @Test
    void tagResourceTagsAreReturnedByDescribe() {
        client.createSecret(b -> b.name("tagged").secretString("x"));
        client.tagResource(
                b ->
                        b.secretId("tagged")
                                .tags(
                                        t -> t.key("env").value("prod"),
                                        t -> t.key("team").value("payments")));

        DescribeSecretResponse described = client.describeSecret(b -> b.secretId("tagged"));
        assertEquals(2, described.tags().size());
        assertEquals(
                "prod",
                described.tags().stream()
                        .filter(t -> t.key().equals("env"))
                        .findFirst()
                        .orElseThrow()
                        .value());

        client.untagResource(b -> b.secretId("tagged").tagKeys("env"));
        DescribeSecretResponse afterUntag = client.describeSecret(b -> b.secretId("tagged"));
        assertEquals(1, afterUntag.tags().size());
        assertEquals("team", afterUntag.tags().get(0).key());
    }

    @Test
    void batchGetSecretValueReturnsStoredValuesAndErrors() {
        client.createSecret(b -> b.name("batch-a").secretString("aaa"));
        client.createSecret(b -> b.name("batch-b").secretString("bbb"));

        var response =
                client.batchGetSecretValue(
                        b -> b.secretIdList("batch-a", "batch-b", "batch-missing"));

        assertEquals(2, response.secretValues().size());
        assertEquals(
                "aaa",
                response.secretValues().stream()
                        .filter(v -> v.name().equals("batch-a"))
                        .findFirst()
                        .orElseThrow()
                        .secretString());
        assertEquals(1, response.errors().size());
        assertEquals("batch-missing", response.errors().get(0).secretId());
        assertEquals("ResourceNotFoundException", response.errors().get(0).errorCode());
    }

    @Test
    void createSecretWithBlankNameIsRejectedAndDoesNotPoisonState() {
        assertThrows(
                SecretsManagerException.class,
                () -> client.createSecret(b -> b.name("").secretString("x")));
        // The rejected create must not have written a malformed entry that breaks ListSecrets.
        assertTrue(client.listSecrets().secretList().isEmpty());
    }

    @Test
    void getRandomPasswordReturnsAValue() {
        assertNotNull(client.getRandomPassword().randomPassword());
        assertFalse(client.getRandomPassword().randomPassword().isBlank());
    }
}
