package io.cloudstub.ssm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit coverage for the key scheme and the parameter-map helpers. */
class SsmKeysTest {

    @Test
    void keyAndNameRoundTripForHierarchicalName() {
        String name = "/prod/db/password";
        String key = SsmKeys.parameterKey(name);
        assertEquals("ssm/parameters//prod/db/password", key);
        assertEquals(name, SsmKeys.nameFromKey(key), "a slash-bearing name must round-trip");
    }

    @Test
    void keyAndNameRoundTripForFlatName() {
        String name = "flat-param";
        assertEquals(name, SsmKeys.nameFromKey(SsmKeys.parameterKey(name)));
    }

    @Test
    void parameterAndTagKeysUseDistinctPrefixes() {
        assertTrue(SsmKeys.parameterKey("x").startsWith(SsmKeys.PARAMETERS_PREFIX));
        assertTrue(SsmKeys.tagsKey("x").startsWith(SsmKeys.TAGS_PREFIX));
        assertFalse(
                SsmKeys.tagsKey("x").startsWith(SsmKeys.PARAMETERS_PREFIX),
                "a tag key must not appear in a parameter listing");
    }

    @Test
    void arnAddsLeadingSlashForFlatNames() {
        assertEquals(SsmParameters.ARN_PREFIX + "/flat", SsmParameters.arn("flat"));
        assertEquals(SsmParameters.ARN_PREFIX + "/prod/db", SsmParameters.arn("/prod/db"));
    }

    @Test
    void unknownTypeFallsBackToString() {
        assertEquals("String", SsmParameters.normalizeType(null));
        assertEquals("String", SsmParameters.normalizeType("Nonsense"));
        assertEquals("SecureString", SsmParameters.normalizeType("SecureString"));
    }

    @Test
    void newVersionStartsAtOneAndIncrements() {
        Map<String, String> first = SsmParameters.newVersion(null, "n", "v", null, null, null);
        assertEquals(1L, SsmParameters.version(first));
        assertEquals("String", first.get("type"));

        Map<String, String> second =
                SsmParameters.newVersion(first, "n", "v2", "StringList", null, null);
        assertEquals(2L, SsmParameters.version(second));
        assertEquals("StringList", second.get("type"));
    }

    @Test
    void newVersionInheritsTypeAndDataTypeOnTypelessOverwrite() {
        Map<String, String> first =
                SsmParameters.newVersion(null, "n", "v", "SecureString", "aws:ec2:image", null);
        Map<String, String> overwrite =
                SsmParameters.newVersion(first, "n", "v2", null, null, null);
        assertEquals("SecureString", overwrite.get("type"), "type must be inherited on overwrite");
        assertEquals(
                "aws:ec2:image",
                overwrite.get("dataType"),
                "dataType must be inherited on overwrite");
    }

    @Test
    void versionOfCorruptEntryIsZeroNotAnException() {
        assertEquals(0L, SsmParameters.version(Map.of("version", "not-a-number")));
        assertEquals(0L, SsmParameters.lastModified(Map.of()));
    }
}
