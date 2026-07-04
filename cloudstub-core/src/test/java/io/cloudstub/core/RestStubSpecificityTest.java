package io.cloudstub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.StubResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pins that overlapping REST-path stubs are disambiguated by pattern specificity rather than
 * registration order, so a broad catch-all registered by one module never shadows a more specific
 * path registered by another. Reproduces the s3 + lambda collision from issue #199.
 */
class RestStubSpecificityTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final CloudStub cloudMock = new CloudStub();

    @AfterEach
    void tearDown() {
        cloudMock.stop();
    }

    /** Registers a broad catch-all matching any two-segment path (S3 GetObject-style). */
    private static final class CatchAllService implements CloudStubService {
        @Override
        public String serviceId() {
            return "catchall";
        }

        @Override
        public void register(CloudStubContext context) {
            context.registrar().registerRestStub(HttpMethod.GET, "/[^/]+/.+", "catchall");
        }
    }

    /** Registers a specific namespaced path (Lambda GetFunction-style) via the handler overload. */
    private static final class SpecificService implements CloudStubService {
        @Override
        public String serviceId() {
            return "specific";
        }

        @Override
        public void register(CloudStubContext context) {
            context.registrar()
                    .registerRestStub(
                            HttpMethod.GET,
                            "/2015-03-31/functions/[^/]+",
                            (req, store) -> StubResponse.json("specific"));
        }
    }

    private String get(String path) throws Exception {
        return HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + cloudMock.port() + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .body();
    }

    @Test
    void specificPathWinsWhenCatchAllRegisteredLast() throws Exception {
        cloudMock.withService(new SpecificService());
        cloudMock.withService(new CatchAllService());
        cloudMock.start();

        assertEquals(
                "specific",
                get("/2015-03-31/functions/fn"),
                "the specific path must win even though the catch-all was registered later");
        assertEquals(
                "catchall",
                get("/my-bucket/some/key"),
                "a path only the catch-all matches must still reach the catch-all");
    }

    @Test
    void specificPathWinsWhenCatchAllRegisteredFirst() throws Exception {
        cloudMock.withService(new CatchAllService());
        cloudMock.withService(new SpecificService());
        cloudMock.start();

        assertEquals(
                "specific",
                get("/2015-03-31/functions/fn"),
                "the specific path must win regardless of module load order");
    }
}
