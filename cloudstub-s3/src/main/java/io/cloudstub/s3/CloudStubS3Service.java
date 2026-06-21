package io.cloudstub.s3;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.Digest;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.Json;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubRequest;
import io.cloudstub.core.spi.StubResponse;
import io.cloudstub.core.spi.StubTemplates;
import io.cloudstub.core.spi.XmlElement;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CloudStub service module for S3.
 *
 * <p>Uses the REST path protocol: each operation is matched by HTTP method and a path regex.
 *
 * <p>The bucket and object operations are <strong>state-backed</strong>: each is a {@link
 * io.cloudstub.core.spi.StubHandler} that reads and writes the shared {@link StateStore}, so an
 * object {@code PutObject}'d in one call is returned by a later {@code GetObject} and reflected in
 * {@code ListObjectsV2}/{@code ListObjects}. State is keyed under the {@code s3/} prefix (see
 * {@link S3Keys}):
 *
 * <ul>
 *   <li>{@code s3/buckets/{bucket}} → bucket metadata (marks the bucket's existence)
 *   <li>{@code s3/buckets/{bucket}/objects/{key}} → the object record (body, content type, ETag)
 * </ul>
 *
 * <p>The remaining sub-resource operations (ACLs, tagging, lifecycle, multipart, …) are served from
 * static Handlebars templates in {@code src/main/resources/templates/}: well-formed but stateless
 * placeholder responses.
 *
 * <p>Not simulated: multipart upload lifecycle, versioning, object metadata beyond content type,
 * and any sub-resource configuration.
 */
public class CloudStubS3Service implements CloudStubService {

    private static final String SERVICE_ID = "s3";
    private static final String XMLNS = "http://s3.amazonaws.com/doc/2006-03-01/";
    private static final String OWNER_ID = "000000000000000000000000000000cloudstub";
    private static final Pattern KEY_ELEMENT = Pattern.compile("<Key>(.*?)</Key>");

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudStubContext context) {
        StubRegistrar registrar = context.registrar();
        // ListObjects (v1) is the bucket-level GET catch-all (GET /<bucket> with no list-type=2).
        // Register it FIRST so every more specific single-segment GET below (GetBucket*,
        // CreateSession,
        // ListObjectsV2, …) overrides it via WireMock's last-registered-wins tie-break — otherwise
        // this
        // catch-all would shadow them. Same ordering rationale as the CreateBucket/DeleteBucket
        // catch-alls.
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+", this::listObjects);
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+/.+?x-id=AbortMultipartUpload",
                StubTemplates.load(CloudStubS3Service.class, "AbortMultipartUpload"));
        registrar.registerRestStub(
                HttpMethod.POST,
                "/[^/]+/.+",
                StubTemplates.load(CloudStubS3Service.class, "CompleteMultipartUpload"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?x-id=CopyObject",
                StubTemplates.load(CloudStubS3Service.class, "CopyObject"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+", this::createBucket);
        registrar.registerRestStub(
                HttpMethod.POST,
                "/[^/]+?metadataConfiguration",
                StubTemplates.load(CloudStubS3Service.class, "CreateBucketMetadataConfiguration"));
        registrar.registerRestStub(
                HttpMethod.POST,
                "/[^/]+?metadataTable",
                StubTemplates.load(
                        CloudStubS3Service.class, "CreateBucketMetadataTableConfiguration"));
        registrar.registerRestStub(
                HttpMethod.POST,
                "/[^/]+/.+?uploads",
                StubTemplates.load(CloudStubS3Service.class, "CreateMultipartUpload"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?session",
                StubTemplates.load(CloudStubS3Service.class, "CreateSession"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+", this::deleteBucket);
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?analytics",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketAnalyticsConfiguration"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?cors",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketCors"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?encryption",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketEncryption"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?intelligent-tiering",
                StubTemplates.load(
                        CloudStubS3Service.class, "DeleteBucketIntelligentTieringConfiguration"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?inventory",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketInventoryConfiguration"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?lifecycle",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketLifecycle"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?metadataConfiguration",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketMetadataConfiguration"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?metadataTable",
                StubTemplates.load(
                        CloudStubS3Service.class, "DeleteBucketMetadataTableConfiguration"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?metrics",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketMetricsConfiguration"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?ownershipControls",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketOwnershipControls"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?policy",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketPolicy"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?replication",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketReplication"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?tagging",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketTagging"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?website",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucketWebsite"));
        // AWS SDK v2 omits ?x-id=DeleteObject when endpointOverride is set; use a plain catch-all.
        // Sub-resource stubs (DeleteObjectTagging etc.) are registered later and win via LIFO.
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+/.+", this::deleteObject);
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+/.+?tagging",
                StubTemplates.load(CloudStubS3Service.class, "DeleteObjectTagging"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+?delete", this::deleteObjects);
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+?publicAccessBlock",
                StubTemplates.load(CloudStubS3Service.class, "DeletePublicAccessBlock"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?abac",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketAbac"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?accelerate",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketAccelerateConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?acl",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketAcl"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?analytics&x-id=GetBucketAnalyticsConfiguration",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketAnalyticsConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?cors",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketCors"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?encryption",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketEncryption"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?intelligent-tiering&x-id=GetBucketIntelligentTieringConfiguration",
                StubTemplates.load(
                        CloudStubS3Service.class, "GetBucketIntelligentTieringConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?inventory&x-id=GetBucketInventoryConfiguration",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketInventoryConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?lifecycle",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketLifecycleConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?location",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketLocation"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?logging",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketLogging"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?metadataConfiguration",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketMetadataConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?metadataTable",
                StubTemplates.load(
                        CloudStubS3Service.class, "GetBucketMetadataTableConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?metrics&x-id=GetBucketMetricsConfiguration",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketMetricsConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?notification",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketNotificationConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?ownershipControls",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketOwnershipControls"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?policy",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketPolicy"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?policyStatus",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketPolicyStatus"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?replication",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketReplication"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?requestPayment",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketRequestPayment"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?tagging",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketTagging"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?versioning",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketVersioning"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?website",
                StubTemplates.load(CloudStubS3Service.class, "GetBucketWebsite"));
        // AWS SDK v2 omits ?x-id=GetObject when endpointOverride is set; use a plain catch-all.
        // Sub-resource stubs (GetObjectAcl, GetObjectTagging etc.) are registered later and win via
        // LIFO.
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+", this::getObject);
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+?acl",
                StubTemplates.load(CloudStubS3Service.class, "GetObjectAcl"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+?attributes",
                StubTemplates.load(CloudStubS3Service.class, "GetObjectAttributes"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+?legal-hold",
                StubTemplates.load(CloudStubS3Service.class, "GetObjectLegalHold"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?object-lock",
                StubTemplates.load(CloudStubS3Service.class, "GetObjectLockConfiguration"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+?retention",
                StubTemplates.load(CloudStubS3Service.class, "GetObjectRetention"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+?tagging",
                StubTemplates.load(CloudStubS3Service.class, "GetObjectTagging"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+?torrent",
                StubTemplates.load(CloudStubS3Service.class, "GetObjectTorrent"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?publicAccessBlock",
                StubTemplates.load(CloudStubS3Service.class, "GetPublicAccessBlock"));
        registrar.registerRestStub(HttpMethod.HEAD, "/[^/]+", this::headBucket);
        registrar.registerRestStub(HttpMethod.HEAD, "/[^/]+/.+", this::headObject);
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?analytics&x-id=ListBucketAnalyticsConfigurations",
                StubTemplates.load(CloudStubS3Service.class, "ListBucketAnalyticsConfigurations"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?intelligent-tiering&x-id=ListBucketIntelligentTieringConfigurations",
                StubTemplates.load(
                        CloudStubS3Service.class, "ListBucketIntelligentTieringConfigurations"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?inventory&x-id=ListBucketInventoryConfigurations",
                StubTemplates.load(CloudStubS3Service.class, "ListBucketInventoryConfigurations"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?metrics&x-id=ListBucketMetricsConfigurations",
                StubTemplates.load(CloudStubS3Service.class, "ListBucketMetricsConfigurations"));
        // The AWS SDK sends ListBuckets as GET / (it omits ?x-id=ListBuckets when the endpoint is
        // overridden), so match the root path with any or no query string.
        registrar.registerRestStub(HttpMethod.GET, "/(\\?.*)?", this::listBuckets);
        registrar.registerRestStub(
                HttpMethod.GET,
                "/?x-id=ListDirectoryBuckets",
                StubTemplates.load(CloudStubS3Service.class, "ListDirectoryBuckets"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?uploads",
                StubTemplates.load(CloudStubS3Service.class, "ListMultipartUploads"));
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+?versions",
                StubTemplates.load(CloudStubS3Service.class, "ListObjectVersions"));
        // ListObjects (v1) catch-all is registered at the top of this method (see comment there).
        // Match list-type=2 anywhere in the query so ListObjectsV2 requests carrying prefix,
        // max-keys,
        // continuation-token, etc. still route here instead of falling through to the v1 catch-all.
        registrar.registerRestStub(
                HttpMethod.GET, "/[^/]+\\?(.*&)?list-type=2(&.*)?", this::listObjectsV2);
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+?x-id=ListParts",
                StubTemplates.load(CloudStubS3Service.class, "ListParts"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?abac",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketAbac"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?accelerate",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketAccelerateConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?acl",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketAcl"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?analytics",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketAnalyticsConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?cors",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketCors"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?encryption",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketEncryption"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?intelligent-tiering",
                StubTemplates.load(
                        CloudStubS3Service.class, "PutBucketIntelligentTieringConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?inventory",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketInventoryConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?lifecycle",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketLifecycleConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?logging",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketLogging"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?metrics",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketMetricsConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?notification",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketNotificationConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?ownershipControls",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketOwnershipControls"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?policy",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketPolicy"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?replication",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketReplication"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?requestPayment",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketRequestPayment"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?tagging",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketTagging"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?versioning",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketVersioning"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?website",
                StubTemplates.load(CloudStubS3Service.class, "PutBucketWebsite"));
        // AWS SDK v2 omits ?x-id=PutObject when endpointOverride is set; use a plain catch-all.
        // Sub-resource stubs (PutObjectAcl, PutObjectTagging etc.) are registered later and win via
        // LIFO.
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+", this::putObject);
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?acl",
                StubTemplates.load(CloudStubS3Service.class, "PutObjectAcl"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?legal-hold",
                StubTemplates.load(CloudStubS3Service.class, "PutObjectLegalHold"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?object-lock",
                StubTemplates.load(CloudStubS3Service.class, "PutObjectLockConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?retention",
                StubTemplates.load(CloudStubS3Service.class, "PutObjectRetention"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?tagging",
                StubTemplates.load(CloudStubS3Service.class, "PutObjectTagging"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?publicAccessBlock",
                StubTemplates.load(CloudStubS3Service.class, "PutPublicAccessBlock"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?renameObject",
                StubTemplates.load(CloudStubS3Service.class, "RenameObject"));
        registrar.registerRestStub(
                HttpMethod.POST,
                "/[^/]+/.+?restore",
                StubTemplates.load(CloudStubS3Service.class, "RestoreObject"));
        registrar.registerRestStub(
                HttpMethod.POST,
                "/[^/]+/.+?select&select-type=2",
                StubTemplates.load(CloudStubS3Service.class, "SelectObjectContent"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?metadataInventoryTable",
                StubTemplates.load(
                        CloudStubS3Service.class,
                        "UpdateBucketMetadataInventoryTableConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+?metadataJournalTable",
                StubTemplates.load(
                        CloudStubS3Service.class, "UpdateBucketMetadataJournalTableConfiguration"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?encryption",
                StubTemplates.load(CloudStubS3Service.class, "UpdateObjectEncryption"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?x-id=UploadPart",
                StubTemplates.load(CloudStubS3Service.class, "UploadPart"));
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+?x-id=UploadPartCopy",
                StubTemplates.load(CloudStubS3Service.class, "UploadPartCopy"));
        registrar.registerRestStub(
                HttpMethod.POST,
                "/WriteGetObjectResponse",
                StubTemplates.load(CloudStubS3Service.class, "WriteGetObjectResponse"));
    }

    // --- Bucket operations -------------------------------------------------------------------

    private StubResponse createBucket(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        store.put(S3Keys.bucketKey(bucket), Json.object("createdAt", Instant.now().toString()));
        return StubResponse.of(200, "application/xml", "").withHeader("Location", "/" + bucket);
    }

    private StubResponse deleteBucket(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        store.delete(S3Keys.bucketKey(bucket));
        store.clear(S3Keys.objectPrefix(bucket));
        return StubResponse.of(204, "application/xml", "");
    }

    private StubResponse headBucket(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        if (store.get(S3Keys.bucketKey(bucket)) == null) {
            return StubResponse.of(404, "application/xml", "");
        }
        return StubResponse.of(200, "application/xml", "")
                .withHeader("x-amz-bucket-region", "us-east-1");
    }

    private StubResponse listBuckets(StubRequest req, StateStore store) {
        XmlElement buckets = XmlElement.of("Buckets");
        for (String key : store.list(S3Keys.BUCKETS_PREFIX)) {
            if (!S3Keys.isBucketMarkerKey(key)) {
                continue;
            }
            Object record = store.get(key);
            if (record == null) {
                continue;
            }
            String name = key.substring(S3Keys.BUCKETS_PREFIX.length());
            buckets.child(
                    XmlElement.of("Bucket")
                            .child("Name", name)
                            .child("CreationDate", field(record, "createdAt")));
        }
        XmlElement root =
                XmlElement.of("ListAllMyBucketsResult")
                        .attr("xmlns", XMLNS)
                        .child(
                                XmlElement.of("Owner")
                                        .child("ID", OWNER_ID)
                                        .child("DisplayName", "cloudstub"))
                        .child(buckets);
        return StubResponse.xml(root);
    }

    // --- Object operations -------------------------------------------------------------------

    private StubResponse putObject(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        String key = S3Helpers.objectKey(req.path());
        String body = req.body();
        String contentType = req.header("Content-Type");
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        String etag = Digest.md5Hex(body);
        int size = body.getBytes(StandardCharsets.UTF_8).length;
        store.put(
                S3Keys.objectKey(bucket, key),
                Json.object(
                        "body", body,
                        "contentType", contentType,
                        "etag", etag,
                        "size", size,
                        "lastModified", Instant.now().toString()));
        return StubResponse.of(200, "application/xml", "").withHeader("ETag", quote(etag));
    }

    private StubResponse getObject(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        String key = S3Helpers.objectKey(req.path());
        Object record = store.get(S3Keys.objectKey(bucket, key));
        if (record == null) {
            return noSuchKey(key);
        }
        return StubResponse.of(200, field(record, "contentType"), field(record, "body"))
                .withHeader("ETag", quote(field(record, "etag")));
    }

    private StubResponse headObject(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        String key = S3Helpers.objectKey(req.path());
        Object record = store.get(S3Keys.objectKey(bucket, key));
        if (record == null) {
            return StubResponse.of(404, "application/xml", "");
        }
        return StubResponse.of(200, field(record, "contentType"), "")
                .withHeader("ETag", quote(field(record, "etag")))
                .withHeader("Content-Length", field(record, "size"));
    }

    private StubResponse deleteObject(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        String key = S3Helpers.objectKey(req.path());
        store.delete(S3Keys.objectKey(bucket, key));
        return StubResponse.of(204, "application/xml", "");
    }

    private StubResponse deleteObjects(StubRequest req, StateStore store) {
        String bucket = S3Helpers.bucket(req.path());
        XmlElement result = XmlElement.of("DeleteResult").attr("xmlns", XMLNS);
        Matcher m = KEY_ELEMENT.matcher(req.body());
        while (m.find()) {
            // The <Key> text is XML-escaped in the request body; unescape it so it matches the key
            // PutObject stored (object records are keyed by the decoded, unescaped key).
            String key = unescapeXml(m.group(1));
            store.delete(S3Keys.objectKey(bucket, key));
            result.child(XmlElement.of("Deleted").child("Key", key));
        }
        return StubResponse.xml(result);
    }

    private StubResponse listObjects(StubRequest req, StateStore store) {
        return listResult(req, store, false);
    }

    private StubResponse listObjectsV2(StubRequest req, StateStore store) {
        return listResult(req, store, true);
    }

    private StubResponse listResult(StubRequest req, StateStore store, boolean v2) {
        String bucket = S3Helpers.bucket(req.path());
        String prefix = req.queryParam("prefix");
        XmlElement root =
                XmlElement.of("ListBucketResult")
                        .attr("xmlns", XMLNS)
                        .child("Name", bucket)
                        .child("Prefix", prefix == null ? "" : prefix)
                        .child("MaxKeys", "1000")
                        .child("IsTruncated", "false");
        int count = 0;
        for (String stateKey : store.list(S3Keys.objectPrefix(bucket))) {
            String key = S3Keys.objectKeyFromStateKey(bucket, stateKey);
            if (prefix != null && !prefix.isEmpty() && !key.startsWith(prefix)) {
                continue;
            }
            Object record = store.get(stateKey);
            if (record == null) {
                continue;
            }
            root.child(
                    XmlElement.of("Contents")
                            .child("Key", key)
                            .child("LastModified", field(record, "lastModified"))
                            .child("ETag", quote(field(record, "etag")))
                            .child("Size", field(record, "size"))
                            .child("StorageClass", "STANDARD"));
            count++;
        }
        if (v2) {
            root.child("KeyCount", String.valueOf(count));
        }
        return StubResponse.xml(root);
    }

    private static StubResponse noSuchKey(String key) {
        XmlElement error =
                XmlElement.of("Error")
                        .child("Code", "NoSuchKey")
                        .child("Message", "The specified key does not exist.")
                        .child("Key", key);
        return StubResponse.of(404, "application/xml", error.render());
    }

    /** Reads a field from a stored object/bucket record as a string, or {@code ""} if absent. */
    private static String field(Object record, String name) {
        if (record instanceof Map<?, ?> map) {
            Object value = map.get(name);
            if (value != null) {
                return value.toString();
            }
        }
        return "";
    }

    private static String quote(String etag) {
        return "\"" + etag + "\"";
    }

    private static String unescapeXml(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
