package io.cloudmock.standalone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudmock.core.CloudMock;
import io.cloudmock.core.spi.CloudMockApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two front doors share one state store: data sent through the AWS-SDK path (mock port) is
 * visible through the REST API path (API port), and vice versa. This is the cross-surface contract
 * behind issue #0049 — the same state, two representations.
 */
class StatefulCrossSurfaceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private CloudMock cloudMock;
    private ApiServer apiServer;
    private int apiPort;
    private SqsClient sqs;

    @BeforeEach
    void setUp() throws Exception {
        cloudMock = new CloudMock();
        cloudMock.start();
        // Discover the module API services from the (runtime) classpath, as StandaloneMain does.
        List<CloudMockApiService> apiServices = ServiceLoader.load(CloudMockApiService.class)
                .stream().map(ServiceLoader.Provider::get).toList();
        apiServer = new ApiServer(cloudMock, 0, apiServices);
        apiServer.start();
        apiPort = apiServer.port();

        sqs = SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }

    @AfterEach
    void tearDown() {
        sqs.close();
        apiServer.stop();
        cloudMock.stop();
    }

    @Test
    void messageSentViaAwsSdkIsVisibleOnTheRestApi() throws Exception {
        String queueUrl = sqs.createQueue(b -> b.queueName("orders")).queueUrl();
        sqs.sendMessage(b -> b.queueUrl(queueUrl).messageBody("hello from the sdk"));

        JsonNode body = getJson("/api/sqs/receive-message?queue=orders");
        JsonNode messages = body.get("messages");
        assertEquals(1, messages.size(), "REST surface must see the SDK-sent message");
        assertEquals("hello from the sdk", messages.get(0).get("body").asText());
    }

    @Test
    void messageSentViaRestApiIsVisibleToTheAwsSdk() throws Exception {
        post("/api/sqs/send-message?queue=invoices&body=hello%20from%20rest");

        Message msg = sqs.receiveMessage(b -> b
                        .queueUrl("http://localhost/000000000000/invoices"))
                .messages().get(0);
        assertEquals("hello from rest", msg.body(), "AWS SDK must see the REST-sent message");
    }

    @Test
    void createdQueueAppearsInRestListQueues() throws Exception {
        sqs.createQueue(b -> b.queueName("shipments"));
        JsonNode queues = getJson("/api/sqs/list-queues").get("queues");
        boolean found = false;
        for (JsonNode q : queues) {
            if (q.asText().endsWith("/shipments")) {
                found = true;
            }
        }
        assertEquals(true, found, "a queue created via the SDK must appear in REST list-queues");
    }

    // send-message is registered as POST; the API server reads params from the query string, so the
    // request body is unused.
    private void post(String path) throws Exception {
        http.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }
}
