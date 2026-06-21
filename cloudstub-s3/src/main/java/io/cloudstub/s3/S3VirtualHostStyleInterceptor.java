package io.cloudstub.s3;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkInternalExecutionAttribute;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.http.SdkHttpRequest;

/**
 * AWS SDK v2 {@link ExecutionInterceptor} that prepends the bucket to the request path when a
 * default-configured (virtual-hosted-style) {@code S3Client} talks to a CloudStub endpoint, so
 * CloudStub's path-style ({@code /{bucket}/{key}}) stubs match without {@code
 * pathStyleAccessEnabled}.
 *
 * <p>Acts only on requests to a loopback host ({@code localhost}, {@code *.localhost}, {@code
 * 127.0.0.1}, {@code ::1}), leaving a real S3 client in the same JVM untouched. Path-style requests
 * already carry the bucket in the resolved endpoint path and are left unchanged.
 *
 * <p>Discovered automatically from {@code
 * software/amazon/awssdk/global/handlers/execution.interceptors}.
 */
public final class S3VirtualHostStyleInterceptor implements ExecutionInterceptor {

    @Override
    public SdkHttpRequest modifyHttpRequest(
            Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
        SdkHttpRequest httpRequest = context.httpRequest();
        Endpoint endpoint =
                executionAttributes.getAttribute(SdkInternalExecutionAttribute.RESOLVED_ENDPOINT);
        URI endpointUri = endpoint != null ? endpoint.url() : httpRequest.getUri();
        if (!isLoopbackHost(endpointUri.getHost())) {
            return httpRequest;
        }
        Optional<String> bucketField = context.request().getValueForField("Bucket", String.class);
        if (bucketField.isEmpty() || bucketField.get().isEmpty()) {
            return httpRequest;
        }
        String bucket = bucketField.get();
        // Path-style already carries the bucket as the first segment of the endpoint's base path,
        // which is applied on top of the request path; leave it alone to avoid a doubled bucket.
        String endpointPath = endpointUri.getRawPath();
        if (endpointPath != null && bucket.equals(S3Helpers.bucket(endpointPath))) {
            return httpRequest;
        }
        String path = httpRequest.encodedPath();
        if (path == null || path.equals("/")) {
            path = "";
        }
        return httpRequest.toBuilder().encodedPath("/" + bucket + path).build();
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host;
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        h = h.toLowerCase(Locale.ROOT);
        return h.equals("localhost")
                || h.endsWith(".localhost")
                || h.equals("127.0.0.1")
                || h.equals("::1");
    }
}
