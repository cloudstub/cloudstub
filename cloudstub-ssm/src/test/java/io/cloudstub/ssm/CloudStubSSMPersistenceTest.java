package io.cloudstub.ssm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Verifies that a parameter written through the AWS SDK survives a full CloudStub restart when a
 * persistent store directory is configured.
 */
class CloudStubSSMPersistenceTest {

    @Test
    void parameterSurvivesRestart(@TempDir Path storeDir) {
        String name = "/durable/param";

        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubSSMService())) {
            cloudMock.start();
            try (SsmClient ssm = client(cloudMock.port())) {
                ssm.putParameter(b -> b.name(name).value("persisted value"));
            }
        }

        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubSSMService())) {
            cloudMock.start();
            try (SsmClient ssm = client(cloudMock.port())) {
                var parameter = ssm.getParameter(b -> b.name(name)).parameter();
                assertEquals("persisted value", parameter.value(), "value must survive a restart");
                assertEquals(1L, parameter.version());
            }
        }
    }

    private static SsmClient client(int port) {
        return SsmClient.builder()
                .endpointOverride(URI.create("http://localhost:" + port))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }
}
