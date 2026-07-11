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

    @Test
    void rewritesGitHubBlobUrlToRawContent() {
        assertEquals(
                "https://raw.githubusercontent.com/aws/api-models-aws/main/models/sqs/service/2012-11-05/sqs-2012-11-05.json",
                HttpsModelResolver.rewriteGitHubBlobUrl(
                        "https://github.com/aws/api-models-aws/blob/main/models/sqs/service/2012-11-05/sqs-2012-11-05.json"));
    }

    @Test
    void httpsResolverDownloadsToProjectLocalDir() throws Exception {
        Path source = Files.writeString(tempDir.resolve("model.json"), "{\"smithy\":\"2.0\"}");
        Path resolved = new HttpsModelResolver(source.toUri().toString()).resolve();
        try {
            assertTrue(resolved.isAbsolute());
            assertTrue(resolved.startsWith(HttpsModelResolver.DOWNLOAD_DIR.toAbsolutePath()));
            assertEquals("model.json", resolved.getFileName().toString());
            assertEquals("{\"smithy\":\"2.0\"}", Files.readString(resolved));
        } finally {
            Files.deleteIfExists(resolved);
        }
    }

    @Test
    void rewriteStripsQueryAndFragmentFromBlobUrl() {
        assertEquals(
                "https://raw.githubusercontent.com/o/r/main/model.json",
                HttpsModelResolver.rewriteGitHubBlobUrl(
                        "https://github.com/o/r/blob/main/model.json?plain=1#L5"));
    }

    @Test
    void leavesRawGitHubUrlUnchanged() {
        String raw =
                "https://raw.githubusercontent.com/aws/api-models-aws/main/models/sqs/service/2012-11-05/sqs-2012-11-05.json";
        assertEquals(raw, HttpsModelResolver.rewriteGitHubBlobUrl(raw));
    }

    @Test
    void leavesNonGitHubUrlUnchanged() {
        String url = "https://example.com/models/model.json";
        assertEquals(url, HttpsModelResolver.rewriteGitHubBlobUrl(url));
    }

    @Test
    void downloadDirIsProjectLocalUnderBuild() {
        assertFalse(HttpsModelResolver.DOWNLOAD_DIR.isAbsolute());
        assertTrue(HttpsModelResolver.DOWNLOAD_DIR.startsWith(Path.of("build")));
    }
}
