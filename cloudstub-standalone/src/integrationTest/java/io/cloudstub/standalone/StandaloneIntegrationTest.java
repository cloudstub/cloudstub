package io.cloudstub.standalone;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

class StandaloneIntegrationTest {

    private static final int PORT = 14566;
    private static StandaloneProcess process;

    @BeforeAll
    static void startServer() throws Exception {
        // Services are opt-in: declare sqs so ListQueues is served.
        process = StandaloneProcess.start(PORT, "--services=sqs");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (process != null) {
            process.close();
        }
    }

    @Test
    void sqsListQueuesIsServedByStandaloneProcess() {
        try (SqsClient sqs =
                SqsClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + PORT))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {

            ListQueuesResponse response = sqs.listQueues();
            assertNotNull(response);
            assertTrue(response.sdkHttpResponse().isSuccessful());
        }
    }
}
