package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.download.ModuleDownloader;
import org.junit.jupiter.api.Test;

class MavenBaseUrlResolverTest {

    @Test
    void defaultsToMavenCentral() {
        assertEquals(
                ModuleDownloader.CENTRAL_BASE_URL, MavenBaseUrlResolver.resolve(new String[] {}));
    }

    @Test
    void flagOverridesDefault() {
        String mirror = "https://mirror.example.com/maven2";
        assertEquals(
                mirror, MavenBaseUrlResolver.resolve(new String[] {"--maven-base-url=" + mirror}));
        assertEquals(
                mirror, MavenBaseUrlResolver.resolve(new String[] {"--maven-base-url", mirror}));
    }

    @Test
    void blankFlagFallsThroughToDefault() {
        assertEquals(
                ModuleDownloader.CENTRAL_BASE_URL,
                MavenBaseUrlResolver.resolve(new String[] {"--maven-base-url="}));
    }
}
