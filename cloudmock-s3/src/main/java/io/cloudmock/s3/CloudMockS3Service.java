package io.cloudmock.s3;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;
import io.cloudmock.core.spi.HttpMethod;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * CloudMock service module for S3.
 *
 * <p>Uses the REST path protocol: each operation is matched by HTTP method and a path regex and
 * served from a Handlebars template in {@code src/main/resources/templates/}.
 */
public class CloudMockS3Service implements CloudMockService {

    private static final String SERVICE_ID = "s3";

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudMockContext context) {
        StubRegistrar registrar = context.registrar();
        // ListObjects (v1) is the bucket-level GET catch-all (GET /<bucket> with no list-type=2).
        // Register it FIRST so every more specific single-segment GET below (GetBucket*, CreateSession,
        // ListObjectsV2, …) overrides it via WireMock's last-registered-wins tie-break — otherwise this
        // catch-all would shadow them. Same ordering rationale as the CreateBucket/DeleteBucket catch-alls.
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+", loadTemplate("ListObjects"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+/.+?x-id=AbortMultipartUpload", loadTemplate("AbortMultipartUpload"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+/.+", loadTemplate("CompleteMultipartUpload"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?x-id=CopyObject", loadTemplate("CopyObject"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+", loadTemplate("CreateBucket"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+?metadataConfiguration", loadTemplate("CreateBucketMetadataConfiguration"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+?metadataTable", loadTemplate("CreateBucketMetadataTableConfiguration"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+/.+?uploads", loadTemplate("CreateMultipartUpload"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?session", loadTemplate("CreateSession"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+", loadTemplate("DeleteBucket"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?analytics", loadTemplate("DeleteBucketAnalyticsConfiguration"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?cors", loadTemplate("DeleteBucketCors"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?encryption", loadTemplate("DeleteBucketEncryption"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?intelligent-tiering", loadTemplate("DeleteBucketIntelligentTieringConfiguration"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?inventory", loadTemplate("DeleteBucketInventoryConfiguration"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?lifecycle", loadTemplate("DeleteBucketLifecycle"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?metadataConfiguration", loadTemplate("DeleteBucketMetadataConfiguration"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?metadataTable", loadTemplate("DeleteBucketMetadataTableConfiguration"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?metrics", loadTemplate("DeleteBucketMetricsConfiguration"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?ownershipControls", loadTemplate("DeleteBucketOwnershipControls"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?policy", loadTemplate("DeleteBucketPolicy"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?replication", loadTemplate("DeleteBucketReplication"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?tagging", loadTemplate("DeleteBucketTagging"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?website", loadTemplate("DeleteBucketWebsite"));
        // AWS SDK v2 omits ?x-id=DeleteObject when endpointOverride is set; use a plain catch-all.
        // Sub-resource stubs (DeleteObjectTagging etc.) are registered later and win via LIFO.
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+/.+", loadTemplate("DeleteObject"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+/.+?tagging", loadTemplate("DeleteObjectTagging"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+?delete", loadTemplate("DeleteObjects"));
        registrar.registerRestStub(HttpMethod.DELETE, "/[^/]+?publicAccessBlock", loadTemplate("DeletePublicAccessBlock"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?abac", loadTemplate("GetBucketAbac"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?accelerate", loadTemplate("GetBucketAccelerateConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?acl", loadTemplate("GetBucketAcl"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?analytics&x-id=GetBucketAnalyticsConfiguration", loadTemplate("GetBucketAnalyticsConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?cors", loadTemplate("GetBucketCors"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?encryption", loadTemplate("GetBucketEncryption"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?intelligent-tiering&x-id=GetBucketIntelligentTieringConfiguration", loadTemplate("GetBucketIntelligentTieringConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?inventory&x-id=GetBucketInventoryConfiguration", loadTemplate("GetBucketInventoryConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?lifecycle", loadTemplate("GetBucketLifecycleConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?location", loadTemplate("GetBucketLocation"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?logging", loadTemplate("GetBucketLogging"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?metadataConfiguration", loadTemplate("GetBucketMetadataConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?metadataTable", loadTemplate("GetBucketMetadataTableConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?metrics&x-id=GetBucketMetricsConfiguration", loadTemplate("GetBucketMetricsConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?notification", loadTemplate("GetBucketNotificationConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?ownershipControls", loadTemplate("GetBucketOwnershipControls"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?policy", loadTemplate("GetBucketPolicy"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?policyStatus", loadTemplate("GetBucketPolicyStatus"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?replication", loadTemplate("GetBucketReplication"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?requestPayment", loadTemplate("GetBucketRequestPayment"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?tagging", loadTemplate("GetBucketTagging"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?versioning", loadTemplate("GetBucketVersioning"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?website", loadTemplate("GetBucketWebsite"));
        // AWS SDK v2 omits ?x-id=GetObject when endpointOverride is set; use a plain catch-all.
        // Sub-resource stubs (GetObjectAcl, GetObjectTagging etc.) are registered later and win via LIFO.
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+", loadTemplate("GetObject"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+?acl", loadTemplate("GetObjectAcl"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+?attributes", loadTemplate("GetObjectAttributes"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+?legal-hold", loadTemplate("GetObjectLegalHold"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?object-lock", loadTemplate("GetObjectLockConfiguration"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+?retention", loadTemplate("GetObjectRetention"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+?tagging", loadTemplate("GetObjectTagging"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+?torrent", loadTemplate("GetObjectTorrent"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?publicAccessBlock", loadTemplate("GetPublicAccessBlock"));
        registrar.registerRestStub(HttpMethod.HEAD, "/[^/]+", loadTemplate("HeadBucket"));
        registrar.registerRestStub(HttpMethod.HEAD, "/[^/]+/.+", loadTemplate("HeadObject"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?analytics&x-id=ListBucketAnalyticsConfigurations", loadTemplate("ListBucketAnalyticsConfigurations"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?intelligent-tiering&x-id=ListBucketIntelligentTieringConfigurations", loadTemplate("ListBucketIntelligentTieringConfigurations"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?inventory&x-id=ListBucketInventoryConfigurations", loadTemplate("ListBucketInventoryConfigurations"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?metrics&x-id=ListBucketMetricsConfigurations", loadTemplate("ListBucketMetricsConfigurations"));
        registrar.registerRestStub(HttpMethod.GET, "/?x-id=ListBuckets", loadTemplate("ListBuckets"));
        registrar.registerRestStub(HttpMethod.GET, "/?x-id=ListDirectoryBuckets", loadTemplate("ListDirectoryBuckets"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?uploads", loadTemplate("ListMultipartUploads"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+?versions", loadTemplate("ListObjectVersions"));
        // ListObjects (v1) catch-all is registered at the top of this method (see comment there).
        // Match list-type=2 anywhere in the query so ListObjectsV2 requests carrying prefix, max-keys,
        // continuation-token, etc. still route here instead of falling through to the v1 catch-all.
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+\\?(.*&)?list-type=2(&.*)?", loadTemplate("ListObjectsV2"));
        registrar.registerRestStub(HttpMethod.GET, "/[^/]+/.+?x-id=ListParts", loadTemplate("ListParts"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?abac", loadTemplate("PutBucketAbac"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?accelerate", loadTemplate("PutBucketAccelerateConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?acl", loadTemplate("PutBucketAcl"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?analytics", loadTemplate("PutBucketAnalyticsConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?cors", loadTemplate("PutBucketCors"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?encryption", loadTemplate("PutBucketEncryption"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?intelligent-tiering", loadTemplate("PutBucketIntelligentTieringConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?inventory", loadTemplate("PutBucketInventoryConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?lifecycle", loadTemplate("PutBucketLifecycleConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?logging", loadTemplate("PutBucketLogging"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?metrics", loadTemplate("PutBucketMetricsConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?notification", loadTemplate("PutBucketNotificationConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?ownershipControls", loadTemplate("PutBucketOwnershipControls"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?policy", loadTemplate("PutBucketPolicy"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?replication", loadTemplate("PutBucketReplication"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?requestPayment", loadTemplate("PutBucketRequestPayment"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?tagging", loadTemplate("PutBucketTagging"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?versioning", loadTemplate("PutBucketVersioning"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?website", loadTemplate("PutBucketWebsite"));
        // AWS SDK v2 omits ?x-id=PutObject when endpointOverride is set; use a plain catch-all.
        // Sub-resource stubs (PutObjectAcl, PutObjectTagging etc.) are registered later and win via LIFO.
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+", loadTemplate("PutObject"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?acl", loadTemplate("PutObjectAcl"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?legal-hold", loadTemplate("PutObjectLegalHold"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?object-lock", loadTemplate("PutObjectLockConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?retention", loadTemplate("PutObjectRetention"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?tagging", loadTemplate("PutObjectTagging"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?publicAccessBlock", loadTemplate("PutPublicAccessBlock"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?renameObject", loadTemplate("RenameObject"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+/.+?restore", loadTemplate("RestoreObject"));
        registrar.registerRestStub(HttpMethod.POST, "/[^/]+/.+?select&select-type=2", loadTemplate("SelectObjectContent"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?metadataInventoryTable", loadTemplate("UpdateBucketMetadataInventoryTableConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+?metadataJournalTable", loadTemplate("UpdateBucketMetadataJournalTableConfiguration"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?encryption", loadTemplate("UpdateObjectEncryption"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?x-id=UploadPart", loadTemplate("UploadPart"));
        registrar.registerRestStub(HttpMethod.PUT, "/[^/]+/.+?x-id=UploadPartCopy", loadTemplate("UploadPartCopy"));
        registrar.registerRestStub(HttpMethod.POST, "/WriteGetObjectResponse", loadTemplate("WriteGetObjectResponse"));
    }

    private static String loadTemplate(String name) {
        String path = "/templates/" + name + ".hbs";
        try (InputStream in = CloudMockS3Service.class.getResourceAsStream(path)) {
            if (in == null)
                throw new IllegalStateException("Template not found: " + path);
            return new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
