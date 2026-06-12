package io.cloudstub.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModulesDirResolverTest {

    @Test
    void parsesEqualsFormFlag(@TempDir Path dir) {
        assertEquals(dir, ModulesDirResolver.resolve(new String[] {"--modules-dir=" + dir}));
    }

    @Test
    void parsesSpaceSeparatedFlag(@TempDir Path dir) {
        assertEquals(
                dir, ModulesDirResolver.resolve(new String[] {"--modules-dir", dir.toString()}));
    }

    @Test
    void blankValueIsTreatedAsUnset() {
        // A blank `--modules-dir=` must not resolve to Path.of("") (the current working directory);
        // it falls through exactly as if no flag were passed.
        assertEquals(
                ModulesDirResolver.resolve(new String[] {}),
                ModulesDirResolver.resolve(new String[] {"--modules-dir="}));
        assertNotEquals(Path.of(""), ModulesDirResolver.resolve(new String[] {"--modules-dir="}));
    }
}
