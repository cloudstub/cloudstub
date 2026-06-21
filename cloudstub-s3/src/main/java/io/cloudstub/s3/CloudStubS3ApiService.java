package io.cloudstub.s3;

import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.core.spi.Digest;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.Json;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.restapi.ApiParam;
import io.cloudstub.core.spi.restapi.ApiRequest;
import io.cloudstub.core.spi.restapi.ApiResponse;
import io.cloudstub.core.spi.restapi.CloudStubApiContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API surface for S3, mounted under {@code /api/s3/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clb s3 <command>} dynamically with no compile-time knowledge of S3.
 *
 * <p>Routes are <em>state-backed</em>: they read and write the same {@link StateStore} (under the
 * same {@link S3Keys} scheme) as the AWS-protocol stubs in {@link CloudStubS3Service}. So an object
 * uploaded through the AWS SDK is returned by {@code GET /api/s3/get-object} and shown in the
 * console, and vice versa — one state, two representations (AWS wire protocol vs. this friendly
 * JSON).
 *
 * <p>Discovered via {@code META-INF/services/io.cloudstub.core.spi.CloudStubApiService}.
 */
public class CloudStubS3ApiService implements CloudStubApiService {

    private static final ApiParam BUCKET = new ApiParam("bucket", true, "Bucket name");
    private static final ApiParam KEY = new ApiParam("key", true, "Object key");

    private StateStore store;

    @Override
    public String serviceId() {
        return "s3";
    }

    @Override
    public void registerRoutes(CloudStubApiContext context) {
        this.store = context.stateStore();
        var r = context.registrar();
        r.register(
                HttpMethod.GET,
                "/list-buckets",
                "list-buckets",
                "List S3 buckets",
                List.of(),
                this::listBuckets);
        r.register(
                HttpMethod.GET,
                "/list-objects",
                "list-objects",
                "List objects in an S3 bucket",
                List.of(BUCKET),
                this::listObjects);
        r.register(
                HttpMethod.PUT,
                "/put-object",
                "put-object",
                "Upload an object to an S3 bucket",
                List.of(BUCKET, KEY, new ApiParam("body", false, "Object content")),
                this::putObject);
        r.register(
                HttpMethod.GET,
                "/get-object",
                "get-object",
                "Download an object from an S3 bucket",
                List.of(BUCKET, KEY),
                this::getObject);
    }

    private ApiResponse listBuckets(ApiRequest req) {
        List<String> buckets = new ArrayList<>();
        for (String key : store.list(S3Keys.BUCKETS_PREFIX)) {
            if (S3Keys.isBucketMarkerKey(key) && store.get(key) != null) {
                buckets.add(key.substring(S3Keys.BUCKETS_PREFIX.length()));
            }
        }
        return new ApiResponse(200, Map.of("buckets", buckets));
    }

    private ApiResponse listObjects(ApiRequest req) {
        String bucket = req.queryParams().getOrDefault("bucket", "");
        List<String> objects = new ArrayList<>();
        for (String stateKey : store.list(S3Keys.objectPrefix(bucket))) {
            if (store.get(stateKey) != null) {
                objects.add(S3Keys.objectKeyFromStateKey(bucket, stateKey));
            }
        }
        return new ApiResponse(200, Map.of("bucket", bucket, "objects", objects));
    }

    private ApiResponse putObject(ApiRequest req) {
        String bucket = req.queryParams().getOrDefault("bucket", "");
        String key = req.queryParams().getOrDefault("key", "");
        String body = req.queryParams().getOrDefault("body", "");
        String etag = Digest.md5Hex(body);
        store.put(
                S3Keys.objectKey(bucket, key),
                Json.object(
                        "body",
                        body,
                        "contentType",
                        "text/plain",
                        "etag",
                        etag,
                        "size",
                        body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        "lastModified",
                        Instant.now().toString()));
        return new ApiResponse(
                200, Map.of("status", "uploaded", "bucket", bucket, "key", key, "etag", etag));
    }

    private ApiResponse getObject(ApiRequest req) {
        String bucket = req.queryParams().getOrDefault("bucket", "");
        String key = req.queryParams().getOrDefault("key", "");
        Object record = store.get(S3Keys.objectKey(bucket, key));
        if (record == null) {
            return new ApiResponse(404, Map.of("bucket", bucket, "key", key, "error", "NoSuchKey"));
        }
        String body = record instanceof Map<?, ?> map ? String.valueOf(map.get("body")) : "";
        return new ApiResponse(200, Map.of("bucket", bucket, "key", key, "body", body));
    }
}
