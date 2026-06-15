package io.cloudstub.s3;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubTemplates;

/**
 * CloudStub service module for S3.
 *
 * <p>Uses the REST path protocol: each operation is matched by HTTP method and a path regex and
 * served from a Handlebars template in {@code src/main/resources/templates/}.
 */
public class CloudStubS3Service implements CloudStubService {

    private static final String SERVICE_ID = "s3";

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
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+",
                StubTemplates.load(CloudStubS3Service.class, "ListObjects"));
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
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+",
                StubTemplates.load(CloudStubS3Service.class, "CreateBucket"));
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
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+",
                StubTemplates.load(CloudStubS3Service.class, "DeleteBucket"));
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
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+/.+",
                StubTemplates.load(CloudStubS3Service.class, "DeleteObject"));
        registrar.registerRestStub(
                HttpMethod.DELETE,
                "/[^/]+/.+?tagging",
                StubTemplates.load(CloudStubS3Service.class, "DeleteObjectTagging"));
        registrar.registerRestStub(
                HttpMethod.POST,
                "/[^/]+?delete",
                StubTemplates.load(CloudStubS3Service.class, "DeleteObjects"));
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
        registrar.registerRestStub(
                HttpMethod.GET,
                "/[^/]+/.+",
                StubTemplates.load(CloudStubS3Service.class, "GetObject"));
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
        registrar.registerRestStub(
                HttpMethod.HEAD,
                "/[^/]+",
                StubTemplates.load(CloudStubS3Service.class, "HeadBucket"));
        registrar.registerRestStub(
                HttpMethod.HEAD,
                "/[^/]+/.+",
                StubTemplates.load(CloudStubS3Service.class, "HeadObject"));
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
        registrar.registerRestStub(
                HttpMethod.GET,
                "/?x-id=ListBuckets",
                StubTemplates.load(CloudStubS3Service.class, "ListBuckets"));
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
                HttpMethod.GET,
                "/[^/]+\\?(.*&)?list-type=2(&.*)?",
                StubTemplates.load(CloudStubS3Service.class, "ListObjectsV2"));
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
        registrar.registerRestStub(
                HttpMethod.PUT,
                "/[^/]+/.+",
                StubTemplates.load(CloudStubS3Service.class, "PutObject"));
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
}
