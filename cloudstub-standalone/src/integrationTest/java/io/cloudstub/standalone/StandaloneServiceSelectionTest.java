package io.cloudstub.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Verifies that {@code --services=sqs} enables only the SQS service: SQS is served while a request
 * that would match the (unloaded) SNS service is not.
 */
class StandaloneServiceSelectionTest {

    private static final int PORT = 14577;
    private static StandaloneProcess process;

    @BeforeAll
    static void startServer() throws Exception {
        process = StandaloneProcess.start(PORT, "--services=sqs");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (process != null) {
            process.close();
        }
    }

    @Test
    void startupLogReportsOnlySqsEnabled() throws Exception {
        assertTrue(
                process.awaitOutput(l -> l.contains("Enabled services: sqs"), 5_000),
                "Expected startup log to report only sqs enabled, got: " + process.output());
    }

    @Test
    void enabledServiceIsServed() {
        try (SqsClient sqs =
                SqsClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + PORT))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {

            assertTrue(sqs.listQueues().sdkHttpResponse().isSuccessful());
        }
    }

    @Test
    void disabledServiceIsNotServed() throws Exception {
        // SNS uses XML/Form routing on Action=Publish. With only sqs enabled, no stub
        // matches this request and WireMock returns 404.
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + PORT + "/"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("Action=Publish&Message=hello"))
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(
                404,
                response.statusCode(),
                "SNS request should not be served when the sns service is not enabled");
    }
}
