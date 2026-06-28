package io.cloudstub.junit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Verifies that a module requested via {@link CloudStubExtension#withModule} which is already on
 * the application classpath is used from there rather than downloaded. {@code cloudstub-sqs} is a
 * test dependency of this module, so pointing downloads at an unreachable repository proves no
 * download is attempted for it.
 */
class CloudStubExtensionClasspathModuleTest {

    @Test
    void requestedModuleOnClasspathIsNotDownloaded(@TempDir Path emptyCache) {
        CloudStubExtension cloudMock =
                new CloudStubExtension()
                        .withMavenBaseUrl("http://localhost:1/unreachable")
                        .withModulesCacheDir(emptyCache)
                        .withModule("sqs");

        cloudMock.beforeAll(null); // would throw if "sqs" were downloaded from the unreachable repo
        try (SqsClient sqs =
                SqsClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build()) {
            assertNotNull(sqs.createQueue(b -> b.queueName("classpath-queue")).queueUrl());
        } finally {
            cloudMock.afterAll(null);
        }
    }
}
