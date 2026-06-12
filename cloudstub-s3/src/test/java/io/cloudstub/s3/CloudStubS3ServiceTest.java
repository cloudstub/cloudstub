package io.cloudstub.s3;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

class CloudStubS3ServiceTest {

    static CloudStub cloudMock;
    static S3Client s3;

    static final String BUCKET = "test-bucket";
    static final String KEY = "test-key";

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubS3Service());
        cloudMock.start();

        s3 =
                S3Client.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(true)
                                        .checksumValidationEnabled(false)
                                        .build())
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
    void createBucketCompletesWithoutException() {
        assertDoesNotThrow(() -> s3.createBucket(b -> b.bucket(BUCKET)));
    }

    @Test
    void putObjectCompletesWithoutException() {
        assertDoesNotThrow(
                () ->
                        s3.putObject(
                                b -> b.bucket(BUCKET).key(KEY),
                                RequestBody.fromString("hello from cloudstub")));
    }

    @Test
    void getObjectReturnsNonEmptyBody() {
        ResponseBytes<GetObjectResponse> response =
                s3.getObject(b -> b.bucket(BUCKET).key(KEY), ResponseTransformer.toBytes());
        assertNotNull(response);
        assertTrue(response.asByteArray().length > 0);
    }

    @Test
    void deleteObjectCompletesWithoutException() {
        assertDoesNotThrow(() -> s3.deleteObject(b -> b.bucket(BUCKET).key(KEY)));
    }

    @Test
    void headObjectCompletesWithoutException() {
        assertDoesNotThrow(() -> s3.headObject(b -> b.bucket(BUCKET).key(KEY)));
    }

    @Test
    void listObjectsV2ReturnsValidResponse() {
        ListObjectsV2Response response = s3.listObjectsV2(b -> b.bucket(BUCKET));
        assertNotNull(response);
        assertFalse(response.isTruncated());
        assertNotNull(response.contents());
    }

    /**
     * Regression for issue #0019 review (finding #1): a ListObjectsV2 call carrying a prefix sends
     * {@code ?list-type=2&prefix=...}, which the old end-anchored pattern did not match — the
     * request fell through to the ListObjects (v1) catch-all and returned the wrong response shape.
     */
    @Test
    void listObjectsV2WithPrefixReturnsValidResponse() {
        ListObjectsV2Response response = s3.listObjectsV2(b -> b.bucket(BUCKET).prefix("logs/"));
        assertNotNull(response);
        assertFalse(response.isTruncated());
        assertNotNull(response.contents());
    }

    /** Finding #1, broader: multiple extra query params must still route to ListObjectsV2. */
    @Test
    void listObjectsV2WithMultipleParamsReturnsValidResponse() {
        ListObjectsV2Response response =
                s3.listObjectsV2(
                        b -> b.bucket(BUCKET).prefix("logs/").maxKeys(10).startAfter("logs/a"));
        assertNotNull(response);
        assertFalse(response.isTruncated());
        assertNotNull(response.contents());
    }

    /**
     * Regression for issue #0019 review (finding #2): the {@code GET /<bucket>} ListObjects
     * catch-all must not shadow bucket-level GET sub-resources. A {@code GET /bucket?acl} must be
     * served by the GetBucketAcl stub, not the ListObjects (v1) stub. Asserted at the routing level
     * (raw HTTP), since the sub-resource templates are placeholders the SDK cannot deserialize.
     */
    @Test
    void bucketSubResourceGetDoesNotLeakToListObjects() throws Exception {
        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder()
                                        .uri(
                                                URI.create(
                                                        "http://localhost:"
                                                                + cloudMock.port()
                                                                + "/"
                                                                + BUCKET
                                                                + "?acl"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("GetBucketAclOutput"),
                "GET /bucket?acl should be served by the GetBucketAcl stub");
        assertFalse(
                response.body().contains("ListObjectsOutput"),
                "GET /bucket?acl must not fall through to the ListObjects catch-all");
    }

    /**
     * Plain {@code GET /bucket} (no list-type=2, no sub-resource) still routes to ListObjects (v1).
     */
    @Test
    void plainBucketGetRoutesToListObjects() throws Exception {
        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder()
                                        .uri(
                                                URI.create(
                                                        "http://localhost:"
                                                                + cloudMock.port()
                                                                + "/"
                                                                + BUCKET))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("ListObjectsOutput"),
                "plain GET /bucket should be served by the ListObjects (v1) catch-all");
    }
}
