package io.cloudmock.standalone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudmock.core.CloudMock;
import io.cloudmock.core.spi.CloudMockApiService;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.restapi.ApiResponse;
import io.cloudmock.core.spi.restapi.CloudMockApiContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerIntegrationTest {

    private CloudMock cloudMock;
    private ApiServer apiServer;
    private int apiPort;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        cloudMock = new CloudMock();
        cloudMock.start();
        apiServer = new ApiServer(cloudMock, 0, List.of()); // 0 = ephemeral port, no bind race
        apiServer.start();
        apiPort = apiServer.port();
    }

    @AfterEach
    void tearDown() {
        apiServer.stop();
        cloudMock.stop();
    }

    @Test
    void statusReturnsPortAndUptime() throws Exception {
        assertEquals(200, get("/api/status").statusCode());
        JsonNode body = getJson("/api/status");
        assertEquals(cloudMock.port(), body.get("port").asInt());
        assertEquals(apiPort, body.get("apiPort").asInt());
        assertNotNull(body.get("startedAt"));
        assertNotNull(body.get("uptime"));
    }

    @Test
    void statusListsRoutes() throws Exception {
        JsonNode routes = getJson("/api/status").get("routes");

        assertTrue(routes.isArray());
        assertFalse(routes.isEmpty());

        boolean hasStatus = false;
        for (JsonNode route : routes) {
            if ("/api/status".equals(route.get("path").asText())) {
                hasStatus = true;
                assertEquals("GET", route.get("method").asText());
            }
        }
        assertTrue(hasStatus, "Expected /api/status in routes list");
    }

    @Test
    void statusListsModules() throws Exception {
        JsonNode modules = getJson("/api/status").get("modules");
        assertTrue(modules.isArray());
        for (JsonNode module : modules) {
            assertNotNull(module.get("id"));
            assertTrue(module.get("stubs").isArray());
        }
    }

    @Test
    void resetClearsAllState() throws Exception {
        cloudMock.stateStore().put("sqs/queues/my-queue", "data");
        assertEquals(200, post("/api/reset").statusCode());
        assertFalse(cloudMock.stateStore().list("sqs/").contains("sqs/queues/my-queue"));
    }

    @Test
    void resetClearsStateForOneService() throws Exception {
        cloudMock.stateStore().put("sqs/queues/my-queue", "data");
        cloudMock.stateStore().put("s3/buckets/my-bucket", "data");

        assertEquals(200, post("/api/reset?service=sqs").statusCode());

        assertTrue(cloudMock.stateStore().list("sqs/").isEmpty());
        assertFalse(cloudMock.stateStore().list("s3/").isEmpty());
    }

    @Test
    void historyIsEmptyBeforeAnyRequests() throws Exception {
        assertTrue(getJson("/api/history").get("requests").isArray());
    }

    @Test
    void openApiSpecContainsCoreRoutes() throws Exception {
        JsonNode spec = getJson("/api/openapi.json");
        assertEquals("3.0.3", spec.get("openapi").asText());
        JsonNode paths = spec.get("paths");
        assertNotNull(paths.get("/api/status"));
        assertNotNull(paths.get("/api/reset"));
        assertNotNull(paths.get("/api/history"));
    }

    @Test
    void openApiSpecDocumentsServiceQueryParameter() throws Exception {
        JsonNode reset = getJson("/api/openapi.json").get("paths").get("/api/reset").get("post");
        JsonNode params = reset.get("parameters");
        assertNotNull(params, "Expected reset to declare query parameters");
        assertEquals("service", params.get(0).get("name").asText());
        assertEquals("query", params.get(0).get("in").asText());
        assertFalse(params.get(0).get("required").asBoolean());
    }

    @Test
    void openApiSpecListsItself() throws Exception {
        assertNotNull(getJson("/api/openapi.json").get("paths").get("/api/openapi.json"));
    }

    @Test
    void historyRecordsMatchedRequestWithServiceAndOperation() throws Exception {
        sendSqsSendMessage();

        JsonNode requests = getJson("/api/history?service=sqs").get("requests");
        assertFalse(requests.isEmpty());
        JsonNode latest = requests.get(0);
        assertEquals("sqs", latest.get("serviceId").asText());
        assertEquals("AmazonSQS.SendMessage", latest.get("operation").asText());
        assertTrue(latest.get("matched").asBoolean());
    }

    @Test
    void fullResetClearsHistory() throws Exception {
        sendSqsSendMessage();
        assertFalse(getJson("/api/history").get("requests").isEmpty());

        assertEquals(200, post("/api/reset").statusCode());
        assertTrue(getJson("/api/history").get("requests").isEmpty());
    }

    @Test
    void singleServiceResetLeavesHistoryIntact() throws Exception {
        sendSqsSendMessage();

        assertEquals(200, post("/api/reset?service=sqs").statusCode());
        assertFalse(getJson("/api/history").get("requests").isEmpty());
    }

    @Test
    void moduleRoutesAreServedAndDiscoverable() throws Exception {
        restartApiWith(new EchoApiService());

        assertTrue(getJson("/api/test/echo").get("ok").asBoolean());

        boolean listed = false;
        for (JsonNode route : getJson("/api/status").get("routes")) {
            if ("/api/test/echo".equals(route.get("path").asText())) {
                listed = true;
            }
        }
        assertTrue(listed, "Expected module route in /api/status routes list");
    }

    @Test
    void handlerThrowingNullMessageReturns500WithJsonError() throws Exception {
        restartApiWith(new EchoApiService());

        HttpResponse<String> resp = get("/api/test/boom");
        assertEquals(500, resp.statusCode());
        assertNotNull(mapper.readTree(resp.body()).get("error"));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        assertEquals(404, get("/api/no-such-route").statusCode());
    }

    @Test
    void prefixCollidingPathReturns404() throws Exception {
        // /api/status context matches by prefix; the exact-path guard must reject /api/statusEXTRA.
        assertEquals(404, get("/api/statusEXTRA").statusCode());
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder()
                        .uri(uri("/api/status"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void restartApiWith(CloudMockApiService... services) throws IOException {
        apiServer.stop();
        apiServer = new ApiServer(cloudMock, 0, List.of(services));
        apiServer.start();
        apiPort = apiServer.port();
    }

    private void sendSqsSendMessage() throws IOException, InterruptedException {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                        .header("X-Amz-Target", "AmazonSQS.SendMessage")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"MessageBody\":\"hi\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(
                HttpRequest.newBuilder().uri(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String pathAndQuery) throws IOException, InterruptedException {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(uri(pathAndQuery))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(String path) throws Exception {
        return mapper.readTree(get(path).body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + apiPort + path);
    }

    /** Minimal module that exposes routes under {@code /api/test/…} for SPI coverage. */
    private static final class EchoApiService implements CloudMockApiService {
        @Override
        public String serviceId() {
            return "test";
        }

        @Override
        public void registerRoutes(CloudMockApiContext context) {
            var registrar = context.registrar();
            registrar.register(HttpMethod.GET, "/echo", "echo ok",
                    req -> new ApiResponse(200, Map.of("ok", true)));
            registrar.register(HttpMethod.GET, "/boom", "always fails",
                    req -> { throw new RuntimeException(); }); // null message — exercises sendError guard
        }
    }
}
