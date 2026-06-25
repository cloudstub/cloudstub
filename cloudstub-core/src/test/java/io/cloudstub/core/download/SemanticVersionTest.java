package io.cloudstub.core.download;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

    private static int compare(String a, String b) {
        return SemanticVersion.parseOrNull(a).compareTo(SemanticVersion.parseOrNull(b));
    }

    @Test
    void ordersNumericFields() {
        assertTrue(compare("0.1.0", "0.2.0") < 0);
        assertTrue(compare("1.0.0", "0.9.9") > 0);
        assertTrue(compare("0.1.2", "0.1.2") == 0);
    }

    @Test
    void releaseOutranksPrerelease() {
        assertTrue(compare("0.1.0", "0.1.0-beta.5") > 0);
        assertTrue(compare("0.1.0-beta.5", "0.1.0") < 0);
    }

    @Test
    void ordersPrereleaseIdentifiers() {
        assertTrue(compare("0.1.0-beta.4", "0.1.0-beta.5") < 0);
        assertTrue(compare("0.1.0-beta.2", "0.1.0-beta.10") < 0);
        assertTrue(compare("0.1.0-alpha", "0.1.0-beta") < 0);
        assertTrue(compare("0.1.0-beta", "0.1.0-beta.1") < 0);
    }

    @Test
    void numericIdentifiersRankBelowAlphanumeric() {
        assertTrue(compare("0.1.0-1", "0.1.0-alpha") < 0);
    }

    @Test
    void rejectsMalformedVersions() {
        assertNull(SemanticVersion.parseOrNull("0.1"));
        assertNull(SemanticVersion.parseOrNull("latest"));
        assertNull(SemanticVersion.parseOrNull(null));
        assertNull(SemanticVersion.parseOrNull(""));
    }
}
