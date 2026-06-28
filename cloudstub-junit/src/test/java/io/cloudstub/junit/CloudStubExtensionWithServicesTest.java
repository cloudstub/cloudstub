package io.cloudstub.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.HttpMethod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Verifies {@link CloudStubExtension#withServices} registers every provided instance. */
class CloudStubExtensionWithServicesTest {

    @RegisterExtension
    static CloudStubExtension cloudMock =
            new CloudStubExtension()
                    .withServices(
                            new PathStub("alpha", "/a", "a"), new PathStub("beta", "/b", "b"));

    private static String get(String path) throws Exception {
        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + cloudMock.port()
                                                                + path))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    @Test
    void bothServicesAreRegistered() throws Exception {
        assertEquals("a", get("/a"));
        assertEquals("b", get("/b"));
    }

    private record PathStub(String serviceId, String path, String body)
            implements CloudStubService {
        @Override
        public void register(CloudStubContext context) {
            context.registrar().registerRestStub(HttpMethod.GET, path, body);
        }
    }
}
