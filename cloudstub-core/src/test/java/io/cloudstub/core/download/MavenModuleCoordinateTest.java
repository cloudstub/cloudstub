package io.cloudstub.core.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MavenModuleCoordinateTest {

    @Test
    void buildsCoordinateFilenamesAndUrl() {
        MavenModuleCoordinate coordinate = new MavenModuleCoordinate("sqs", "0.1.0");

        assertEquals("io.github.cloudstub:cloudstub-sqs:0.1.0", coordinate.displayCoordinate());
        assertEquals("cloudstub-sqs-0.1.0.jar", coordinate.jarFileName());
        assertEquals("cloudstub-sqs.jar", coordinate.unversionedJarFileName());
        assertEquals("cloudstub-sqs-", coordinate.versionedJarPrefix());
        assertEquals(
                "https://repo1.maven.org/maven2/io/github/cloudstub/cloudstub-sqs/0.1.0/"
                        + "cloudstub-sqs-0.1.0.jar.sha512",
                coordinate.artifactUrl("https://repo1.maven.org/maven2", "jar.sha512"));
    }

    @Test
    void requireFileSystemSafeRejectsPathSeparatorsAndTraversal() {
        new MavenModuleCoordinate("sqs", "0.1.0-beta.1").requireFileSystemSafe();

        assertThrows(
                ModuleDownloadException.class,
                () -> new MavenModuleCoordinate("../evil", "0.1.0").requireFileSystemSafe());
        assertThrows(
                ModuleDownloadException.class,
                () -> new MavenModuleCoordinate("sqs", "../../etc/passwd").requireFileSystemSafe());
        assertThrows(
                ModuleDownloadException.class,
                () -> new MavenModuleCoordinate("sqs/nested", "0.1.0").requireFileSystemSafe());
    }
}
