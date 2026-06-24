package io.cloudstub.s3;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;

class CloudStubS3ServiceTest {

    static CloudStub cloudMock;
    static S3Client s3;
    static S3Client credentialedS3;

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

        // Credentialed client: the SDK signs the payload, frames the body aws-chunked, and
        // validates the returned ETag. Checksum validation left at its default (enabled).
        credentialedS3 =
                S3Client.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .serviceConfiguration(
                                S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test-key", "test-secret")))
                        .region(Region.US_EAST_1)
                        .build();
    }

    @AfterAll
    static void stop() {
        s3.close();
        credentialedS3.close();
        cloudMock.stop();
    }

    /**
     * The core round-trip: an object's body survives put → get, the listing reflects the real key
     * and count, and a delete removes it. This is the behaviour issue #142 makes state-backed.
     */
    @Test
    void putObjectBodyIsReturnedByGetObject() {
        String bucket = "roundtrip-bucket";
        String key = "hello.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("hi there"));

        ResponseBytes<GetObjectResponse> got =
                s3.getObject(b -> b.bucket(bucket).key(key), ResponseTransformer.toBytes());
        assertEquals("hi there", new String(got.asByteArray(), StandardCharsets.UTF_8));
    }

    /** A credentialed (payload-signing, aws-chunked) PutObject passes checksum validation. */
    @Test
    void credentialedPutObjectPassesChecksumAndRoundTrips() {
        String bucket = "credentialed-bucket";
        String key = "signed.txt";
        String content = "content uploaded by a signing client";
        credentialedS3.createBucket(b -> b.bucket(bucket));
        credentialedS3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString(content));

        ResponseBytes<GetObjectResponse> got =
                credentialedS3.getObject(
                        b -> b.bucket(bucket).key(key), ResponseTransformer.toBytes());
        assertEquals(content, new String(got.asByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void listObjectsV2ReflectsRealKeysAndCount() {
        String bucket = "listv2-bucket";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key("a.txt"), RequestBody.fromString("a"));
        s3.putObject(b -> b.bucket(bucket).key("b.txt"), RequestBody.fromString("bb"));

        ListObjectsV2Response response = s3.listObjectsV2(b -> b.bucket(bucket));
        assertEquals(bucket, response.name());
        assertEquals(2, response.keyCount());
        assertEquals(
                java.util.List.of("a.txt", "b.txt"),
                response.contents().stream().map(o -> o.key()).sorted().toList());
    }

    @Test
    void listObjectsV2HonoursPrefix() {
        String bucket = "prefix-bucket";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key("logs/x"), RequestBody.fromString("1"));
        s3.putObject(b -> b.bucket(bucket).key("data/y"), RequestBody.fromString("2"));

        ListObjectsV2Response response = s3.listObjectsV2(b -> b.bucket(bucket).prefix("logs/"));
        assertEquals(1, response.keyCount());
        assertEquals("logs/x", response.contents().get(0).key());
    }

    @Test
    void deleteObjectRemovesState() {
        String bucket = "delete-bucket";
        String key = "gone.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("bye"));
        s3.deleteObject(b -> b.bucket(bucket).key(key));

        assertThrows(
                NoSuchKeyException.class,
                () -> s3.getObject(b -> b.bucket(bucket).key(key), ResponseTransformer.toBytes()));
    }

    @Test
    void getMissingObjectThrowsNoSuchKey() {
        String bucket = "missing-bucket";
        s3.createBucket(b -> b.bucket(bucket));
        assertThrows(
                NoSuchKeyException.class,
                () ->
                        s3.getObject(
                                b -> b.bucket(bucket).key("nope"), ResponseTransformer.toBytes()));
    }

    @Test
    void listBucketsReflectsCreatedBuckets() {
        s3.createBucket(b -> b.bucket("listbuckets-one"));
        s3.createBucket(b -> b.bucket("listbuckets-two"));

        var names = s3.listBuckets().buckets().stream().map(x -> x.name()).toList();
        assertTrue(names.contains("listbuckets-one"));
        assertTrue(names.contains("listbuckets-two"));
    }

    @Test
    void headBucketReflectsExistence() {
        String bucket = "head-bucket";
        s3.createBucket(b -> b.bucket(bucket));
        assertDoesNotThrow(() -> s3.headBucket(b -> b.bucket(bucket)));

        assertThrows(
                NoSuchBucketException.class, () -> s3.headBucket(b -> b.bucket("never-created")));
    }

    @Test
    void headObjectReflectsExistence() {
        String bucket = "headobj-bucket";
        String key = "present.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("data"));
        assertEquals(4L, s3.headObject(b -> b.bucket(bucket).key(key)).contentLength());

        assertThrows(
                S3Exception.class, () -> s3.headObject(b -> b.bucket(bucket).key("absent.txt")));
    }

    /**
     * Keys requiring URL-encoding (spaces, {@code &}) must round-trip: the wire path is
     * percent-encoded and the DeleteObjects body is XML-escaped, but get/list/delete address the
     * object by its real decoded key.
     */
    @Test
    void encodedKeyRoundTrips() {
        String bucket = "encoded-bucket";
        String key = "my report & notes.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("payload"));

        ResponseBytes<GetObjectResponse> got =
                s3.getObject(b -> b.bucket(bucket).key(key), ResponseTransformer.toBytes());
        assertEquals("payload", new String(got.asByteArray(), StandardCharsets.UTF_8));
        assertEquals(
                java.util.List.of(key),
                s3.listObjectsV2(b -> b.bucket(bucket)).contents().stream()
                        .map(o -> o.key())
                        .toList());

        s3.deleteObjects(b -> b.bucket(bucket).delete(d -> d.objects(o -> o.key(key))));
        assertEquals(0, s3.listObjectsV2(b -> b.bucket(bucket)).keyCount());
    }

    @Test
    void deleteBucketClearsObjects() {
        String bucket = "deletebucket-bucket";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key("k"), RequestBody.fromString("v"));
        s3.deleteBucket(b -> b.bucket(bucket));

        assertThrows(NoSuchBucketException.class, () -> s3.headBucket(b -> b.bucket(bucket)));
    }

    @Test
    void objectTaggingRoundTrips() {
        String bucket = "objtag-bucket";
        String key = "tagged.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("x"));

        s3.putObjectTagging(
                b ->
                        b.bucket(bucket)
                                .key(key)
                                .tagging(
                                        t ->
                                                t.tagSet(
                                                        Tag.builder()
                                                                .key("env")
                                                                .value("prod")
                                                                .build(),
                                                        Tag.builder()
                                                                .key("team")
                                                                .value("a&b")
                                                                .build())));

        assertEquals(
                java.util.Map.of("env", "prod", "team", "a&b"),
                s3.getObjectTagging(b -> b.bucket(bucket).key(key)).tagSet().stream()
                        .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value)));

        s3.deleteObjectTagging(b -> b.bucket(bucket).key(key));
        assertTrue(s3.getObjectTagging(b -> b.bucket(bucket).key(key)).tagSet().isEmpty());
    }

    @Test
    void untaggedObjectReturnsEmptyTagSet() {
        String bucket = "objnotag-bucket";
        String key = "plain.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("x"));

        assertTrue(s3.getObjectTagging(b -> b.bucket(bucket).key(key)).tagSet().isEmpty());
    }

    @Test
    void bucketTaggingRoundTrips() {
        String bucket = "buckettag-bucket";
        s3.createBucket(b -> b.bucket(bucket));

        s3.putBucketTagging(
                b ->
                        b.bucket(bucket)
                                .tagging(
                                        t ->
                                                t.tagSet(
                                                        Tag.builder()
                                                                .key("owner")
                                                                .value("ops")
                                                                .build())));

        assertEquals(
                java.util.Map.of("owner", "ops"),
                s3.getBucketTagging(b -> b.bucket(bucket)).tagSet().stream()
                        .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value)));

        s3.deleteBucketTagging(b -> b.bucket(bucket));
        assertNoSuchTagSet(() -> s3.getBucketTagging(b -> b.bucket(bucket)));
    }

    @Test
    void untaggedBucketReturns404NoSuchTagSet() {
        String bucket = "bucketnotag-bucket";
        s3.createBucket(b -> b.bucket(bucket));

        assertNoSuchTagSet(() -> s3.getBucketTagging(b -> b.bucket(bucket)));
    }

    @Test
    void deletingObjectClearsItsTags() {
        String bucket = "objtagclear-bucket";
        String key = "k.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("x"));
        s3.putObjectTagging(
                b ->
                        b.bucket(bucket)
                                .key(key)
                                .tagging(t -> t.tagSet(Tag.builder().key("a").value("1").build())));
        s3.deleteObject(b -> b.bucket(bucket).key(key));

        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("y"));
        assertTrue(
                s3.getObjectTagging(b -> b.bucket(bucket).key(key)).tagSet().isEmpty(),
                "a re-created object must not inherit the deleted object's tags");
    }

    @Test
    void putObjectOverwriteClearsTags() {
        String bucket = "objoverwrite-bucket";
        String key = "k.txt";
        s3.createBucket(b -> b.bucket(bucket));
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("v1"));
        s3.putObjectTagging(
                b ->
                        b.bucket(bucket)
                                .key(key)
                                .tagging(t -> t.tagSet(Tag.builder().key("a").value("1").build())));

        // Overwrite the object without deleting it first; real S3 drops the prior tag set.
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString("v2"));
        assertTrue(s3.getObjectTagging(b -> b.bucket(bucket).key(key)).tagSet().isEmpty());
    }

    @Test
    void taggingMissingObjectThrowsNoSuchKey() {
        String bucket = "objtagmissing-bucket";
        s3.createBucket(b -> b.bucket(bucket));

        assertThrows(
                NoSuchKeyException.class,
                () -> s3.getObjectTagging(b -> b.bucket(bucket).key("nope")));
        assertThrows(
                NoSuchKeyException.class,
                () ->
                        s3.putObjectTagging(
                                b ->
                                        b.bucket(bucket)
                                                .key("nope")
                                                .tagging(
                                                        t ->
                                                                t.tagSet(
                                                                        Tag.builder()
                                                                                .key("a")
                                                                                .value("1")
                                                                                .build()))));
    }

    @Test
    void bucketTaggingOnMissingBucketThrowsNoSuchBucket() {
        assertThrows(
                NoSuchBucketException.class,
                () -> s3.getBucketTagging(b -> b.bucket("never-created-tag-bucket")));
    }

    private static void assertNoSuchTagSet(org.junit.jupiter.api.function.Executable call) {
        S3Exception e = assertThrows(S3Exception.class, call);
        assertEquals(404, e.statusCode());
        assertEquals("NoSuchTagSet", e.awsErrorDetails().errorCode());
    }

    /**
     * Regression for issue #0019 review (finding #2): the {@code GET /<bucket>} ListObjects
     * catch-all must not shadow bucket-level GET sub-resources. A {@code GET /bucket?acl} must be
     * served by the GetBucketAcl stub, not the list stub. Asserted at the routing level (raw HTTP),
     * since the sub-resource templates are placeholders the SDK cannot deserialize.
     */
    @Test
    void bucketSubResourceGetDoesNotLeakToListObjects() throws Exception {
        s3.createBucket(b -> b.bucket("routing-bucket"));
        HttpResponse<String> response = rawGet("/routing-bucket?acl");

        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("GetBucketAclOutput"),
                "GET /bucket?acl should be served by the GetBucketAcl stub");
        assertFalse(
                response.body().contains("ListBucketResult"),
                "GET /bucket?acl must not fall through to the ListObjects catch-all");
    }

    /**
     * Plain {@code GET /bucket} (no list-type=2, no sub-resource) still routes to ListObjects (v1),
     * which now returns a real {@code ListBucketResult} naming the bucket.
     */
    @Test
    void plainBucketGetRoutesToListObjects() throws Exception {
        s3.createBucket(b -> b.bucket("plainget-bucket"));
        HttpResponse<String> response = rawGet("/plainget-bucket");

        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("<ListBucketResult"),
                "plain GET /bucket should be served by the ListObjects (v1) stub");
        assertTrue(
                response.body().contains("<Name>plainget-bucket</Name>"),
                "ListObjects (v1) should name the requested bucket");
    }

    private static HttpResponse<String> rawGet(String pathAndQuery) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                "http://localhost:"
                                                        + cloudMock.port()
                                                        + pathAndQuery))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
