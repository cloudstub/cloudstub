package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModuleVersionResolverTest {

    @Test
    void flagOverridesRunningVersion() {
        assertEquals(
                "1.2.3", ModuleVersionResolver.resolve(new String[] {"--module-version=1.2.3"}));
        assertEquals(
                "1.2.3", ModuleVersionResolver.resolve(new String[] {"--module-version", "1.2.3"}));
    }

    @Test
    void defaultsToRunningCoreVersion() {
        // No flag/env: falls back to the build-stamped core version, which is never blank.
        String resolved = ModuleVersionResolver.resolve(new String[] {});
        assertEquals(io.cloudstub.core.download.CoreVersion.current(), resolved);
    }
}
