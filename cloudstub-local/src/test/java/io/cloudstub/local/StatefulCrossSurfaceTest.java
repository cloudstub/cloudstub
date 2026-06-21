package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudstub.core.CloudStub;
import io.cloudstub.core.spi.CloudStubApiService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * The two front doors share one state store: data sent through the AWS-SDK path (mock port) is
 * visible through the REST API path (API port), and vice versa. This is the cross-surface contract
 * behind issue #0049 — the same state, two representations.
 */
class StatefulCrossSurfaceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private CloudStub cloudMock;
    private LocalServer localServer;
    private int apiPort;
    private SqsClient sqs;
    private SecretsManagerClient secrets;
    private S3Client s3;

    @BeforeEach
    void setUp() throws Exception {
        cloudMock = new CloudStub();
        cloudMock.start();
        // Discover the module API services from the (runtime) classpath, as LocalMain does.
        List<CloudStubApiService> apiServices =
                ServiceLoader.load(CloudStubApiService.class).stream()
                        .map(ServiceLoader.Provider::get)
                        .toList();
        localServer = new LocalServer(cloudMock, 0, apiServices);
        localServer.start();
        apiPort = localServer.port();

        sqs =
                SqsClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
        secrets =
                SecretsManagerClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
        s3 =
                S3Client.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(true)
                                        .checksumValidationEnabled(false)
                                        .build())
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
    }

    @AfterEach
    void tearDown() {
        s3.close();
        secrets.close();
        sqs.close();
        localServer.stop();
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

        Message msg =
                sqs.receiveMessage(b -> b.queueUrl("http://localhost/000000000000/invoices"))
                        .messages()
                        .get(0);
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

    @Test
    void secretCreatedViaAwsSdkIsVisibleOnTheRestApi() throws Exception {
        secrets.createSecret(b -> b.name("api-key").secretString("from-the-sdk"));

        JsonNode body = getJson("/api/secretsmanager/get?name=api-key");
        assertEquals(
                "from-the-sdk",
                body.get("secretString").asText(),
                "REST surface must see the SDK-created secret");
    }

    @Test
    void secretPutViaRestApiIsVisibleToTheAwsSdk() throws Exception {
        // The Secrets Manager put route is registered as PUT (the query string carries the params).
        http.send(
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "http://localhost:"
                                                + apiPort
                                                + "/api/secretsmanager/put?name=db-password&value=from-rest"))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(
                "from-rest",
                secrets.getSecretValue(b -> b.secretId("db-password")).secretString(),
                "AWS SDK must see the REST-created secret");
    }

    @Test
    void objectPutViaAwsSdkIsVisibleOnTheRestApi() throws Exception {
        s3.createBucket(b -> b.bucket("assets"));
        s3.putObject(b -> b.bucket("assets").key("readme.txt"), RequestBody.fromString("from-sdk"));

        JsonNode body = getJson("/api/s3/get-object?bucket=assets&key=readme.txt");
        assertEquals(
                "from-sdk",
                body.get("body").asText(),
                "REST surface must see the SDK-uploaded object");
    }

    @Test
    void objectPutViaRestApiIsVisibleToTheAwsSdk() throws Exception {
        http.send(
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "http://localhost:"
                                                + apiPort
                                                + "/api/s3/put-object?bucket=docs&key=note.txt&body=from-rest"))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        ResponseBytes<GetObjectResponse> got =
                s3.getObject(b -> b.bucket("docs").key("note.txt"), ResponseTransformer.toBytes());
        assertEquals("from-rest", got.asUtf8String(), "AWS SDK must see the REST-uploaded object");
    }

    // send-message is registered as POST; the API server reads params from the query string, so the
    // request body is unused.
    private void post(String path) throws Exception {
        http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> resp =
                http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + apiPort + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }
}
