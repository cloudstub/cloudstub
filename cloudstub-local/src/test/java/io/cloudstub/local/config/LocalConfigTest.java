package io.cloudstub.local.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cloudstub.local.config.exception.LocalConfigException;
import io.cloudstub.local.config.resolver.ApiPortResolver;
import io.cloudstub.local.config.resolver.AutoDownloadResolver;
import io.cloudstub.local.config.resolver.MavenBaseUrlResolver;
import io.cloudstub.local.config.resolver.MaxHistoryResolver;
import io.cloudstub.local.config.resolver.ModuleVersionResolver;
import io.cloudstub.local.config.resolver.PortResolver;
import io.cloudstub.local.config.resolver.ServiceSelector;
import io.cloudstub.local.config.resolver.StoreDirectoryResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalConfigTest {

    private static String[] configArgs(Path file) {
        return new String[] {"--config=" + file};
    }

    private static Path write(Path dir, String contents) throws IOException {
        Path file = dir.resolve("cloudstub.properties");
        Files.writeString(file, contents);
        return file;
    }

    @Test
    void loadsPortAndServicesFromFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, "cloudstub.port=9000\ncloudstub.services=sqs,s3\n");
        LocalConfig config = LocalConfig.load(configArgs(file));

        assertEquals(9000, PortResolver.resolve(new String[0], config));
        assertEquals(Set.of("sqs", "s3"), ServiceSelector.resolve(new String[0], config));
    }

    @Test
    void coversEveryResolverKey(@TempDir Path dir) throws IOException {
        Path file =
                write(
                        dir,
                        "cloudstub.port=9000\n"
                                + "cloudstub.api-port=9001\n"
                                + "cloudstub.services=sqs\n"
                                + "cloudstub.store-dir=none\n"
                                + "cloudstub.max-history=42\n"
                                + "cloudstub.module-version=1.2.3\n"
                                + "cloudstub.maven-base-url=https://mirror.example/maven2\n"
                                + "cloudstub.auto-download=false\n");
        LocalConfig config = LocalConfig.load(configArgs(file));

        assertEquals(9000, PortResolver.resolve(new String[0], config));
        assertEquals(9001, ApiPortResolver.resolve(new String[0], config));
        assertEquals(Set.of("sqs"), ServiceSelector.resolve(new String[0], config));
        assertNull(StoreDirectoryResolver.resolve(new String[0], config));
        assertEquals(42, MaxHistoryResolver.resolve(new String[0], config));
        assertEquals("1.2.3", ModuleVersionResolver.resolve(new String[0], config));
        assertEquals(
                "https://mirror.example/maven2",
                MavenBaseUrlResolver.resolve(new String[0], config));
        assertFalse(AutoDownloadResolver.isEnabled(new String[0], config));
    }

    @Test
    void maxHistoryUnlimitedKeywordFromFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, "cloudstub.max-history=unlimited\n");
        assertEquals(
                0, MaxHistoryResolver.resolve(new String[0], LocalConfig.load(configArgs(file))));
    }

    @Test
    void cliFlagOverridesConfigFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, "cloudstub.port=9000\ncloudstub.services=sqs\n");
        LocalConfig config = LocalConfig.load(configArgs(file));

        assertEquals(4566, PortResolver.resolve(new String[] {"--port=4566"}, config));
        assertEquals(Set.of("s3"), ServiceSelector.resolve(new String[] {"--services=s3"}, config));
    }

    @Test
    void absentDefaultFileIsNotAnError() {
        // No --config and no file at the default location: an empty config, every key absent.
        LocalConfig config = LocalConfig.load(new String[0]);
        assertEquals(PortResolver.DEFAULT_PORT, PortResolver.resolve(new String[0], config));
        assertNull(ServiceSelector.resolve(new String[0], config));
    }

    @Test
    void emptyConfigFallsThroughToDefaults() {
        LocalConfig config = LocalConfig.empty();
        assertEquals(PortResolver.DEFAULT_PORT, PortResolver.resolve(new String[0], config));
        assertNull(ServiceSelector.resolve(new String[0], config));
        assertTrue(AutoDownloadResolver.isEnabled(new String[0], config));
    }

    @Test
    void explicitMissingConfigFails(@TempDir Path dir) {
        Path missing = dir.resolve("absent.properties");
        LocalConfigException ex =
                assertThrows(
                        LocalConfigException.class,
                        () -> LocalConfig.load(new String[] {"--config=" + missing}));
        assertTrue(ex.getMessage().contains("absent.properties"));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void unknownKeyFailsNamingFileAndKey(@TempDir Path dir) throws IOException {
        Path file = write(dir, "cloudstub.port=9000\ncloudstub.bogus=x\n");
        LocalConfigException ex =
                assertThrows(LocalConfigException.class, () -> LocalConfig.load(configArgs(file)));
        assertTrue(ex.getMessage().contains("cloudstub.bogus"));
        assertTrue(ex.getMessage().contains(file.toString()));
    }

    @Test
    void nonNumericPortFailsNamingKey(@TempDir Path dir) throws IOException {
        Path file = write(dir, "cloudstub.port=not-a-number\n");
        LocalConfig config = LocalConfig.load(configArgs(file));
        LocalConfigException ex =
                assertThrows(
                        LocalConfigException.class,
                        () -> PortResolver.resolve(new String[0], config));
        assertTrue(ex.getMessage().contains("cloudstub.port"));
        assertTrue(ex.getMessage().contains("not-a-number"));
    }
}
