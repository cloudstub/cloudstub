package io.cloudmock.s3;

import io.cloudmock.core.spi.CloudMockApiService;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.restapi.ApiParam;
import io.cloudmock.core.spi.restapi.ApiRequest;
import io.cloudmock.core.spi.restapi.ApiResponse;
import io.cloudmock.core.spi.restapi.ApiRouteRegistrar;

import java.util.List;
import java.util.Map;

/**
 * REST API surface for S3, mounted under {@code /api/s3/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clm s3 <command>} dynamically with no compile-time knowledge of S3.
 * Responses are synthetic and stateless.
 *
 * <p>Discovered via {@code META-INF/services/io.cloudmock.core.spi.CloudMockApiService}.
 */
public class CloudMockS3ApiService implements CloudMockApiService {

    private static final ApiParam BUCKET = new ApiParam("bucket", true, "Bucket name");
    private static final ApiParam KEY = new ApiParam("key", true, "Object key");

    @Override
    public String serviceId() {
        return "s3";
    }

    @Override
    public void registerRoutes(ApiRouteRegistrar r) {
        r.register(HttpMethod.GET, "/list-buckets", "list-buckets",
                "List S3 buckets", List.of(), this::listBuckets);
        r.register(HttpMethod.GET, "/list-objects", "list-objects",
                "List objects in an S3 bucket", List.of(BUCKET), this::listObjects);
        r.register(HttpMethod.PUT, "/put-object", "put-object",
                "Upload an object to an S3 bucket",
                List.of(BUCKET, KEY, new ApiParam("body", false, "Object content")), this::putObject);
        r.register(HttpMethod.GET, "/get-object", "get-object",
                "Download an object from an S3 bucket", List.of(BUCKET, KEY), this::getObject);
    }

    private ApiResponse listBuckets(ApiRequest req) {
        return new ApiResponse(200, Map.of("buckets", List.of()));
    }

    private ApiResponse listObjects(ApiRequest req) {
        return new ApiResponse(200, Map.of(
                "bucket", req.queryParams().getOrDefault("bucket", ""),
                "objects", List.of()));
    }

    private ApiResponse putObject(ApiRequest req) {
        return new ApiResponse(200, Map.of(
                "status", "uploaded",
                "bucket", req.queryParams().getOrDefault("bucket", ""),
                "key", req.queryParams().getOrDefault("key", "")));
    }

    private ApiResponse getObject(ApiRequest req) {
        return new ApiResponse(200, Map.of(
                "bucket", req.queryParams().getOrDefault("bucket", ""),
                "key", req.queryParams().getOrDefault("key", ""),
                "body", "cloudmock-synthetic-object"));
    }
}
