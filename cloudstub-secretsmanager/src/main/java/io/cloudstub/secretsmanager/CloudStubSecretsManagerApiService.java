package io.cloudstub.secretsmanager;

import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.restapi.ApiParam;
import io.cloudstub.core.spi.restapi.ApiRequest;
import io.cloudstub.core.spi.restapi.ApiResponse;
import io.cloudstub.core.spi.restapi.CloudStubApiContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API surface for Secrets Manager, mounted under {@code /api/secretsmanager/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clb secretsmanager <command>} dynamically with no compile-time knowledge of
 * Secrets Manager.
 *
 * <p>Routes are <em>state-backed</em>: they read and write the same {@link StateStore} (under the
 * same {@link SecretsManagerKeys} scheme) as the AWS-protocol stubs in {@link
 * CloudStubSecretsManagerService}. So a secret created through the AWS SDK is returned by {@code
 * GET /api/secretsmanager/get} and shown in the console, and vice versa — one state, two
 * representations (AWS wire protocol vs. this friendly JSON).
 *
 * <p>Discovered via {@code META-INF/services/io.cloudstub.core.spi.CloudStubApiService}.
 */
public class CloudStubSecretsManagerApiService implements CloudStubApiService {

    private static final ApiParam NAME = new ApiParam("name", true, "Secret name");

    private StateStore store;

    @Override
    public String serviceId() {
        return "secretsmanager";
    }

    @Override
    public void registerRoutes(CloudStubApiContext context) {
        this.store = context.stateStore();
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
        r.register(
                HttpMethod.DELETE,
                "/delete",
                "delete",
                "Delete a secret",
                List.of(NAME),
                this::delete);
    }

    private ApiResponse list(ApiRequest req) {
        List<String> names = new ArrayList<>();
        for (String key : store.list(SecretsManagerKeys.SECRETS_PREFIX)) {
            if (read(key) != null) {
                names.add(SecretsManagerKeys.nameOf(key));
            }
        }
        return new ApiResponse(200, Map.of("secrets", names));
    }

    private ApiResponse get(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        Map<String, String> secret = read(SecretsManagerKeys.secretKey(name));
        if (secret == null) {
            return new ApiResponse(404, Map.of("error", "secret not found", "name", name));
        }
        return new ApiResponse(
                200,
                Map.of(
                        "name", secret.get("name"),
                        "arn", secret.get("arn"),
                        "versionId", secret.get("versionId"),
                        "secretString", secret.get("secretString")));
    }

    private ApiResponse put(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        String value = req.queryParams().getOrDefault("value", "");
        synchronized (store) {
            Map<String, String> secret = read(SecretsManagerKeys.secretKey(name));
            if (secret == null) {
                secret = new LinkedHashMap<>();
                secret.put("arn", SecretsManagerArns.arn(name));
                secret.put("name", name);
                secret.put("description", "");
                secret.put("createdDate", String.valueOf(Instant.now().getEpochSecond()));
            }
            String versionId = UUID.randomUUID().toString();
            secret.put("secretString", value);
            secret.put("versionId", versionId);
            store.put(SecretsManagerKeys.secretKey(name), secret);
            return new ApiResponse(
                    200, Map.of("name", name, "arn", secret.get("arn"), "versionId", versionId));
        }
    }

    private ApiResponse delete(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        store.delete(SecretsManagerKeys.secretKey(name));
        return new ApiResponse(200, Map.of("status", "deleted", "name", name));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> read(String key) {
        Object value = store.get(key);
        return value instanceof Map ? (Map<String, String>) value : null;
    }
}
