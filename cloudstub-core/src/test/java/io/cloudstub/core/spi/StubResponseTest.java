package io.cloudstub.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StubResponseTest {

    @Test
    void jsonMapSerialisesAndSetsAwsJsonContentType() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Name", "db-password");
        body.put("VersionStages", List.of("AWSCURRENT"));
        body.put("CreatedDate", 1718870400L);

        StubResponse response = StubResponse.json(body);

        assertEquals(200, response.status());
        assertEquals(StubResponse.CONTENT_TYPE_JSON, response.contentType());
        assertEquals(
                "{\"Name\":\"db-password\",\"VersionStages\":[\"AWSCURRENT\"],"
                        + "\"CreatedDate\":1718870400}",
                response.body());
    }

    @Test
    void jsonMapEscapesStringValues() {
        StubResponse response = StubResponse.json(Map.of("SecretString", "a\"b\\c\nd"));

        assertEquals("{\"SecretString\":\"a\\\"b\\\\c\\nd\"}", response.body());
    }

    @Test
    void jsonMapWithStatusUsedForErrorBodies() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("__type", "ResourceNotFoundException");
        body.put("Message", "Secrets Manager can't find the specified secret.");

        StubResponse response = StubResponse.json(400, body);

        assertEquals(400, response.status());
        assertEquals(StubResponse.CONTENT_TYPE_JSON, response.contentType());
        assertEquals(
                "{\"__type\":\"ResourceNotFoundException\",\"Message\":"
                        + "\"Secrets Manager can't find the specified secret.\"}",
                response.body());
    }

    @Test
    void jsonMapRejectsNonSerialisableValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StubResponse.json(Map.of("bad", new Object())));
    }
}
