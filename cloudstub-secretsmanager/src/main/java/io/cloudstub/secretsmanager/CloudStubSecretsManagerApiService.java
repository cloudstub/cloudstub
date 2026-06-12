package io.cloudstub.secretsmanager;

import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.restapi.ApiParam;
import io.cloudstub.core.spi.restapi.ApiRequest;
import io.cloudstub.core.spi.restapi.ApiResponse;
import io.cloudstub.core.spi.restapi.CloudStubApiContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API surface for Secrets Manager, mounted under {@code /api/secretsmanager/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clm secretsmanager <command>} dynamically with no compile-time knowledge of
 * Secrets Manager. Responses are synthetic and stateless.
 *
 * <p>Discovered via {@code META-INF/services/io.cloudstub.core.spi.CloudStubApiService}.
 */
public class CloudStubSecretsManagerApiService implements CloudStubApiService {

    private static final String ARN_PREFIX =
            "arn:aws:secretsmanager:us-east-1:000000000000:secret:";

    private static final ApiParam NAME = new ApiParam("name", true, "Secret name");

    @Override
    public String serviceId() {
        return "secretsmanager";
    }

    @Override
    public void registerRoutes(CloudStubApiContext context) {
        var r = context.registrar();
        r.register(HttpMethod.GET, "/list", "list", "List secrets", List.of(), this::list);
        r.register(HttpMethod.GET, "/get", "get", "Get a secret value", List.of(NAME), this::get);
        r.register(
                HttpMethod.PUT,
                "/put",
                "put",
                "Create or update a secret value",
                List.of(NAME, new ApiParam("value", true, "Secret value")),
                this::put);
    }

    private ApiResponse list(ApiRequest req) {
        return new ApiResponse(200, Map.of("secrets", List.of("cloudstub-secret")));
    }

    private ApiResponse get(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        return new ApiResponse(
                200,
                Map.of(
                        "name",
                        name,
                        "arn",
                        ARN_PREFIX + name,
                        "secretString",
                        "{\"username\":\"test\",\"password\":\"test\"}"));
    }

    private ApiResponse put(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        return new ApiResponse(
                200,
                Map.of(
                        "name",
                        name,
                        "arn",
                        ARN_PREFIX + name,
                        "versionId",
                        UUID.randomUUID().toString()));
    }
}
