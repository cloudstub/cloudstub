package io.cloudstub.ssm;

import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.restapi.ApiParam;
import io.cloudstub.core.spi.restapi.ApiRequest;
import io.cloudstub.core.spi.restapi.ApiResponse;
import io.cloudstub.core.spi.restapi.CloudStubApiContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API surface for SSM Parameter Store, mounted under {@code /api/ssm/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code cloudstub ssm <command>} dynamically.
 *
 * <p>Routes are <em>state-backed</em>: they read and write the same {@link StateStore} (under the
 * same {@link SsmKeys} scheme, via the same {@link SsmParameters} map builder) as the AWS-protocol
 * stubs in {@link CloudStubSSMService}. A parameter written through the AWS SDK is returned by
 * {@code GET /api/ssm/get-parameter}, and one written here is returned by the SDK.
 *
 * <p>Discovered via {@code META-INF/services/io.cloudstub.core.spi.CloudStubApiService}.
 */
public class CloudStubSSMApiService implements CloudStubApiService {

    private static final ApiParam NAME = new ApiParam("name", true, "Parameter name");

    private StateStore store;

    @Override
    public String serviceId() {
        return "ssm";
    }

    @Override
    public void registerRoutes(CloudStubApiContext context) {
        this.store = context.stateStore();
        var r = context.registrar();
        r.register(
                HttpMethod.GET,
                "/list-parameters",
                "list-parameters",
                "List stored SSM parameters",
                List.of(),
                this::listParameters);
        r.register(
                HttpMethod.GET,
                "/get-parameter",
                "get-parameter",
                "Get a stored SSM parameter",
                List.of(NAME),
                this::getParameter);
        r.register(
                HttpMethod.POST,
                "/put-parameter",
                "put-parameter",
                "Create or overwrite an SSM parameter",
                List.of(
                        NAME,
                        new ApiParam("value", true, "Parameter value"),
                        new ApiParam("type", false, "String | StringList | SecureString")),
                this::putParameter);
        r.register(
                HttpMethod.POST,
                "/delete-parameter",
                "delete-parameter",
                "Delete an SSM parameter",
                List.of(NAME),
                this::deleteParameter);
    }

    private ApiResponse listParameters(ApiRequest req) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (String key : store.list(SsmKeys.PARAMETERS_PREFIX)) {
            Map<String, String> parameter = SsmParameters.read(store, key);
            if (parameter != null) {
                parameters.add(SsmParameters.metadataShape(parameter));
            }
        }
        return new ApiResponse(200, Map.of("parameters", parameters));
    }

    private ApiResponse getParameter(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        Map<String, String> parameter = SsmParameters.read(store, SsmKeys.parameterKey(name));
        if (parameter == null) {
            return new ApiResponse(404, Map.of("error", "Parameter " + name + " not found."));
        }
        return new ApiResponse(200, Map.of("parameter", SsmParameters.parameterShape(parameter)));
    }

    private ApiResponse putParameter(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        String value = req.queryParams().getOrDefault("value", "");
        String type = req.queryParams().get("type");
        synchronized (store) {
            Map<String, String> parameter =
                    SsmParameters.newVersion(
                            SsmParameters.read(store, SsmKeys.parameterKey(name)),
                            name,
                            value,
                            type,
                            null,
                            null);
            store.put(SsmKeys.parameterKey(name), parameter);
            return new ApiResponse(
                    200, Map.of("name", name, "version", SsmParameters.version(parameter)));
        }
    }

    private ApiResponse deleteParameter(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        synchronized (store) {
            boolean existed = SsmParameters.read(store, SsmKeys.parameterKey(name)) != null;
            store.delete(SsmKeys.parameterKey(name));
            store.delete(SsmKeys.tagsKey(name));
            return new ApiResponse(200, Map.of("name", name, "deleted", existed));
        }
    }
}
