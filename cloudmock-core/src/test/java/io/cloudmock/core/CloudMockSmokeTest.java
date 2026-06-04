package io.cloudmock.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the core networking layer is alive: CloudMock boots and the embedded
 * server accepts real HTTP connections. A WireMock 404 is a pass — this test asserts
 * reachability only. AWS SDK integration is validated in the individual module test suites.
 */
class CloudMockSmokeTest {

    private final CloudMock cloudMock = new CloudMock();

    @BeforeEach
    void start() {
        cloudMock.start();
    }

    @AfterEach
    void stop() {
        cloudMock.stop();
    }

    @Test
    void embeddedServerAcceptsHttpConnections() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                .POST(HttpRequest.BodyPublishers.ofString("Action=ListQueues"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        // Any HTTP response means the server is reachable; 404 from WireMock is expected and fine.
        assertTrue(response.statusCode() > 0);
    }
}
