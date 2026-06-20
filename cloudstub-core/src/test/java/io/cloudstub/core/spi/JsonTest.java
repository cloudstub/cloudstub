package io.cloudstub.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void buildsOrderedObjectFromPairs() {
        Map<String, Object> object =
                Json.object("Name", "db-password", "VersionStages", List.of("AWSCURRENT"));

        assertEquals("db-password", object.get("Name"));
        assertEquals(List.of("AWSCURRENT"), object.get("VersionStages"));
        assertEquals(List.of("Name", "VersionStages"), List.copyOf(object.keySet()));
    }

    @Test
    void emptyArgsBuildEmptyObject() {
        assertEquals(Map.of(), Json.object());
    }

    @Test
    void oddArgumentCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Json.object("only-a-key"));
    }
}
