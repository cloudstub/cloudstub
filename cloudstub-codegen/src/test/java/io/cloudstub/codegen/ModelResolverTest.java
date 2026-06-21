package io.cloudstub.codegen;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelResolverTest {

    @TempDir Path tempDir;

    @Test
    void ofReturnsLocalResolverForFilePath() {
        assertInstanceOf(LocalModelResolver.class, ModelResolver.of("/some/model.json"));
    }

    @Test
    void ofReturnsHttpsResolverForHttpsUrl() {
        assertInstanceOf(
                HttpsModelResolver.class, ModelResolver.of("https://example.com/model.json"));
    }

    @Test
    void ofReturnsHttpResolverForHttpUrl() {
        assertInstanceOf(
                HttpModelResolver.class, ModelResolver.of("http://example.com/model.json"));
    }

    @Test
    void httpResolverAlwaysThrows() {
        assertThrows(
                IllegalArgumentException.class,
                ModelResolver.of("http://example.com/model.json")::resolve);
    }

    @Test
    void localResolverRejectsWrongExtension() throws Exception {
        Path file = Files.writeString(tempDir.resolve("model.txt"), "{}");
        assertThrows(IllegalArgumentException.class, ModelResolver.of(file.toString())::resolve);
    }

    @Test
    void localResolverRejectsMissingFile() {
        assertThrows(
                IllegalArgumentException.class,
                ModelResolver.of(tempDir.resolve("missing.json").toString())::resolve);
    }

    @Test
    void localResolverRejectsDirectory() {
        assertThrows(IllegalArgumentException.class, ModelResolver.of(tempDir.toString())::resolve);
    }

    @Test
    void localResolverReturnsNormalizedPathForValidFile() throws Exception {
        URL fixture = getClass().getResource("/fixtures/widget-service.json");
        assertNotNull(fixture, "widget-service.json fixture missing");
        Path resolved = ModelResolver.of(Path.of(fixture.toURI()).toString()).resolve();
        assertTrue(Files.exists(resolved));
        assertTrue(resolved.isAbsolute());
    }
}
