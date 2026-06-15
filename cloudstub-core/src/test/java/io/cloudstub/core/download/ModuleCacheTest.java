package io.cloudstub.core.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleCacheTest {

    @Test
    void containsMatchesRequestedVersionOrUnversionedJar(@TempDir Path dir) throws IOException {
        ModuleCache cache = new ModuleCache(dir);
        assertFalse(cache.contains(new MavenModuleCoordinate("sqs", "0.1.0")));

        Files.createFile(dir.resolve("cloudstub-sqs-0.1.0.jar"));
        assertTrue(cache.contains(new MavenModuleCoordinate("sqs", "0.1.0")));
        assertFalse(cache.contains(new MavenModuleCoordinate("sqs", "0.2.0")));
        assertFalse(cache.contains(new MavenModuleCoordinate("sns", "0.1.0")));

        Files.createFile(dir.resolve("cloudstub-sns.jar"));
        assertTrue(cache.contains(new MavenModuleCoordinate("sns", "0.1.0")));
        assertTrue(cache.contains(new MavenModuleCoordinate("sns", "9.9.9")));
    }

    @Test
    void storeWritesJarAndPrunesOtherVersionsButKeepsUnversioned(@TempDir Path dir)
            throws IOException {
        Files.createFile(dir.resolve("cloudstub-sqs-0.1.0.jar"));
        Files.createFile(dir.resolve("cloudstub-sns.jar"));

        Path written =
                new ModuleCache(dir)
                        .store(
                                new MavenModuleCoordinate("sqs", "0.2.0"),
                                "jar".getBytes(StandardCharsets.UTF_8));

        assertEquals(dir.resolve("cloudstub-sqs-0.2.0.jar"), written);
        assertEquals("jar", Files.readString(written));
        assertFalse(Files.exists(dir.resolve("cloudstub-sqs-0.1.0.jar")));
        assertTrue(Files.exists(dir.resolve("cloudstub-sns.jar")));
    }
}
