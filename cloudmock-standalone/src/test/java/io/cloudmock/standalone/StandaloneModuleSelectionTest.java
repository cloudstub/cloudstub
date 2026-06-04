package io.cloudmock.standalone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code --modules=sqs} enables only the SQS module: SQS is served while a
 * request that would match the (unloaded) SNS module is not.
 */
class StandaloneModuleSelectionTest {

    private static final int PORT = 14577;
    private static StandaloneProcess process;

    @BeforeAll
    static void startServer() throws Exception {
        process = StandaloneProcess.start(PORT, "--modules=sqs");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (process != null) {
            process.close();
        }
    }

    @Test
    void startupLogReportsOnlySqsEnabled() {
        assertTrue(
                process.output().stream().anyMatch(l -> l.contains("Enabled modules: sqs")),
                "Expected startup log to report only sqs enabled, got: " + process.output());
    }

    @Test
    void enabledModuleIsServed() {
        try (SqsClient sqs = SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + PORT))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build()) {

            assertTrue(sqs.listQueues().sdkHttpResponse().isSuccessful());
        }
    }

    @Test
    void disabledModuleIsNotServed() throws Exception {
        // SNS uses XML/Form routing on Action=Publish. With only sqs enabled, no stub
        // matches this request and WireMock returns 404.
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("Action=Publish&Message=hello"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode(),
                "SNS request should not be served when the sns module is not enabled");
    }
}
