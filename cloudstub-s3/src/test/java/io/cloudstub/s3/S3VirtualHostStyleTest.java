package io.cloudstub.s3;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * Proves a <strong>default-configured</strong> {@link S3Client} (no {@code pathStyleAccessEnabled},
 * no {@code checksumValidationEnabled} override) works against CloudStub for the core operations.
 * The client uses virtual-hosted-style addressing; {@link S3VirtualHostStyleInterceptor},
 * discovered from the classpath, rewrites the requests to path-style so the existing path-regex
 * stubs match.
 */
class S3VirtualHostStyleTest {

    static CloudStub cloudMock;
    static S3Client s3;

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubS3Service());
        cloudMock.start();

        // A vanilla client: only the endpoint is overridden, exactly as an app under test would
        // configure it. No CloudStub-specific service configuration.
        s3 =
                S3Client.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
    }

    @AfterAll
    static void stop() {
        s3.close();
        cloudMock.stop();
    }

    @Test
    void defaultClientRoundTripsThroughVirtualHostedStyle() {
        String bucket = "vhost-bucket";
        String key = "hello.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("hi there"));

        ResponseBytes<GetObjectResponse> got =
                s3.getObject(b -> b.bucket(bucket).key(key), ResponseTransformer.toBytes());
        assertEquals("hi there", new String(got.asByteArray(), StandardCharsets.UTF_8));

        ListObjectsV2Response response = s3.listObjectsV2(b -> b.bucket(bucket));
        assertEquals(bucket, response.name());
        assertEquals(1, response.keyCount());
        assertEquals(key, response.contents().get(0).key());
    }
}
