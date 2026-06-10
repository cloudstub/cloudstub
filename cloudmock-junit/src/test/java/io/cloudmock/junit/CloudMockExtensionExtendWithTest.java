package io.cloudmock.junit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code @ExtendWith} usage mode — no {@code @RegisterExtension} field,
 * just the annotation. CloudMock must start before the first test and stop after the last.
 */
@ExtendWith(CloudMockExtension.class)
class CloudMockExtensionExtendWithTest {

    @Test
    void serverStartsWithAnnotationAlone() {
        String endpointUrl = System.getProperty("aws.endpoint-url");
        assertNotNull(endpointUrl,
                "@ExtendWith should have set aws.endpoint-url but it was null");
        assertTrue(endpointUrl.startsWith("http://localhost:"),
                "aws.endpoint-url should point at localhost, was: " + endpointUrl);
    }
}