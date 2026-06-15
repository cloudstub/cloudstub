package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Boots the standalone server from a {@code cloudstub.properties} file (no {@code --port} / {@code
 * --services} flags) and verifies the file drives the bound port and the enabled service set end to
 * end.
 */
class LocalConfigFileTest {

    private static final int PORT = 14590;
    private static LocalProcess process;

    @BeforeAll
    static void startServer(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("cloudstub.properties");
        Files.writeString(
                config,
                "cloudstub.port="
                        + PORT
                        + "\n"
                        + "cloudstub.api-port="
                        + (PORT + 1000)
                        + "\n"
                        + "cloudstub.services=sqs\n");
        process = LocalProcess.startWithConfig(PORT, config);
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (process != null) {
            process.close();
        }
    }

    @Test
    void startupLogReportsServiceFromConfig() throws Exception {
        assertTrue(
                process.awaitOutput(l -> l.contains("Enabled services: sqs")),
                "Expected the config file's service set to be enabled, got: " + process.output());
    }

    @Test
    void serverBoundOnConfiguredPort() {
        try (SqsClient sqs =
                SqsClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + PORT))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {

            assertTrue(sqs.listQueues().sdkHttpResponse().isSuccessful());
        }
    }
}
