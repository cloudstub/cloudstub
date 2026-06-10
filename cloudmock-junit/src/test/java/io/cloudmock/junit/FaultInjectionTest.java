package io.cloudmock.junit;

import io.cloudmock.secretsmanager.CloudMockSecretsManagerService;
import io.cloudmock.sqs.CloudMockSqsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the fault injection annotation API.
 *
 * <p>Each test drives real AWS SDK v2 clients against a live CloudMock instance and asserts
 * that the correct SDK exception is thrown (or not thrown) for each fault scenario. The
 * fault-leak tests ({@link #afterSqsThrottleNormalResponseReturned} and
 * {@link #afterSqsTimeoutNormalResponseReturned}) are the most important: they verify that
 * {@link CloudMockExtension} always clears faults between test methods.
 *
 * <p>Tag: {@code fault-injection} — exclude from fast local runs with
 * {@code -Dgroups='!fault-injection'} or by configuring {@code test { excludeTags ... }} in
 * Gradle.
 */
@Tag("fault-injection")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FaultInjectionTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension()
            .withService(new CloudMockSqsService())
            .withService(new CloudMockSecretsManagerService());

    static SqsClient sqsClient;
    static SecretsManagerClient smClient;

    static final String QUEUE_URL = "http://localhost/000000000000/test-queue";
    static final String SECRET_ID = "test-secret";

    @BeforeAll
    static void buildClients() {
        URI endpoint = URI.create("http://localhost:" + cloudMock.port());
        sqsClient = SqsClient.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
        smClient = SecretsManagerClient.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }

    @AfterAll
    static void closeClients() {
        sqsClient.close();
        smClient.close();
    }

    // ── @SimulateThrottle ─────────────────────────────────────────────────────

    @Test
    @Order(1)
    @SimulateThrottle(service = "sqs")
    void sqsThrottleThrowsSqsExceptionWithThrottlingCode() {
        SqsException ex = assertThrows(SqsException.class, () ->
                sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
        assertEquals("ThrottlingException", ex.awsErrorDetails().errorCode());
    }

    @Test
    @Order(2)
    void afterSqsThrottleNormalResponseReturned() {
        // Verifies that CloudMockExtension cleared the throttle fault after the previous test.
        assertDoesNotThrow(() ->
                sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
    }

    @Test
    @Order(3)
    @SimulateThrottle(service = "secretsmanager")
    void smThrottleThrowsSecretsManagerExceptionWithThrottlingCode() {
        SecretsManagerException ex = assertThrows(SecretsManagerException.class, () ->
                smClient.getSecretValue(b -> b.secretId(SECRET_ID)));
        assertEquals("ThrottlingException", ex.awsErrorDetails().errorCode());
    }

    // ── @SimulateTimeout ──────────────────────────────────────────────────────

    @Test
    @Order(4)
    @SimulateTimeout(service = "sqs")
    void sqsTimeoutThrowsApiCallTimeoutException() {
        // Use a short apiCallTimeout so the test completes quickly rather than waiting
        // for the full 30-second server-side delay injected by @SimulateTimeout.
        try (SqsClient shortTimeoutClient = SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofMillis(500)))
                .build()) {
            assertThrows(ApiCallTimeoutException.class, () ->
                    shortTimeoutClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
        }
    }

    @Test
    @Order(5)
    void afterSqsTimeoutNormalResponseReturned() {
        // Verifies that CloudMockExtension cleared the timeout fault after the previous test.
        assertDoesNotThrow(() ->
                sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
    }

    // ── @SimulateNetworkBrownout ──────────────────────────────────────────────

    @Test
    @Order(6)
    @SimulateNetworkBrownout(service = "sqs", rate = 1.0)
    void sqsBrownoutRate1EveryRequestFailsWithConnectionError() {
        assertThrows(SdkClientException.class, () ->
                sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
    }

    @Test
    @Order(7)
    @SimulateNetworkBrownout(service = "sqs", rate = 0.0)
    void sqsBrownoutRate0AllRequestsSucceed() {
        assertDoesNotThrow(() ->
                sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
    }

    // ── Multiple annotations ──────────────────────────────────────────────────

    @Test
    @Order(8)
    @SimulateThrottle(service = "sqs")
    @SimulateThrottle(service = "secretsmanager")
    void multipleAnnotationsApplySimultaneously() {
        assertThrows(SqsException.class, () ->
                sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
        assertThrows(SecretsManagerException.class, () ->
                smClient.getSecretValue(b -> b.secretId(SECRET_ID)));
    }
}
