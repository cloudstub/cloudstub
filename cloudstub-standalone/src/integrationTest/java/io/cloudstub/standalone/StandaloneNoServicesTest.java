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

/**
 * Verifies the default standalone behaviour: with no {@code --services} selection, no service is
 * loaded, an actionable warning is logged, and no AWS request is served.
 */
class StandaloneNoServicesTest {

    private static final int PORT = 14588;
    private static StandaloneProcess process;

    @BeforeAll
    static void startServer() throws Exception {
        // No --services flag: the server must start but enable nothing.
        process = StandaloneProcess.start(PORT);
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (process != null) {
            process.close();
        }
    }

    @Test
    void warnsThatNoServicesAreEnabled() throws Exception {
        assertTrue(
                process.awaitOutput(l -> l.contains("Enabled services: (none)")),
                "Expected startup log to report no services enabled, got: " + process.output());
    }

    @Test
    void warningTellsDeveloperHowToEnableServices() throws Exception {
        assertTrue(
                process.awaitOutput(
                        l -> l.contains("--services=") && l.contains("CLOUDSTUB_SERVICES")),
                "Expected an actionable warning naming --services / CLOUDSTUB_SERVICES, got: "
                        + process.output());
    }

    @Test
    void noServiceIsServed() throws Exception {
        // An SQS request that would match the (unloaded) sqs service returns 404 — nothing is
        // wired.
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + PORT))
                        .header("X-Amz-Target", "AmazonSQS.ListQueues")
                        .header("Content-Type", "application/x-amz-json-1.0")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(
                404,
                response.statusCode(),
                "No service should be served when --services is not specified");
    }
}
