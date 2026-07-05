package io.cloudstub.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * Verifies that Lambda state written through the AWS SDK survives a full CloudStub restart when a
 * persistent store directory is configured.
 */
class CloudStubLambdaPersistenceTest {

    @Test
    void functionSurvivesRestart(@TempDir Path storeDir) {
        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubLambdaService())) {
            cloudMock.start();
            try (LambdaClient lambda = client(cloudMock.port())) {
                lambda.createFunction(
                        b ->
                                b.functionName("durable")
                                        .runtime("python3.12")
                                        .role("arn:aws:iam::000000000000:role/lambda-role")
                                        .handler("app.handler")
                                        .code(c -> c.zipFile(SdkBytes.fromUtf8String("code"))));
            }
        }

        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubLambdaService())) {
            cloudMock.start();
            try (LambdaClient lambda = client(cloudMock.port())) {
                var config = lambda.getFunctionConfiguration(b -> b.functionName("durable"));
                assertEquals(
                        "python3.12", config.runtimeAsString(), "function must survive restart");
                assertEquals("app.handler", config.handler());
            }
        }
    }

    private static LambdaClient client(int port) {
        return LambdaClient.builder()
                .endpointOverride(URI.create("http://localhost:" + port))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }
}
