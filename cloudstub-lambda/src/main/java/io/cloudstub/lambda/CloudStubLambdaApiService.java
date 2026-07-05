package io.cloudstub.lambda;

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
 * REST API surface for Lambda, mounted under {@code /api/lambda/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clb lambda <command>} dynamically with no compile-time knowledge of Lambda.
 *
 * <p>Routes are <em>state-backed</em>: they read the same {@link StateStore} (under the same {@link
 * LambdaKeys} scheme) as the AWS-protocol stubs in {@link CloudStubLambdaService}, so a function
 * created through the AWS SDK is listed by {@code GET /api/lambda/list-functions} and shown in the
 * console. The surface is read-oriented; creating and invoking functions goes through the AWS SDK.
 *
 * <p>Discovered via {@code META-INF/services/io.cloudstub.core.spi.CloudStubApiService}.
 */
public class CloudStubLambdaApiService implements CloudStubApiService {

    private static final ApiParam NAME = new ApiParam("name", true, "Function name");

    private StateStore store;

    @Override
    public String serviceId() {
        return "lambda";
    }

    @Override
    public void registerRoutes(CloudStubApiContext context) {
        this.store = context.stateStore();
        var r = context.registrar();
        r.register(
                HttpMethod.GET,
                "/list-functions",
                "list-functions",
                "List Lambda functions",
                List.of(),
                this::listFunctions);
        r.register(
                HttpMethod.GET,
                "/get-function",
                "get-function",
                "Show a Lambda function's configuration",
                List.of(NAME),
                this::getFunction);
    }

    private ApiResponse listFunctions(ApiRequest req) {
        List<Object> functions = new ArrayList<>();
        for (String key : store.list(LambdaKeys.FUNCTIONS_PREFIX)) {
            Object config = store.get(key);
            if (config != null) {
                functions.add(config);
            }
        }
        return new ApiResponse(200, Map.of("functions", functions));
    }

    private ApiResponse getFunction(ApiRequest req) {
        String name = req.queryParams().getOrDefault("name", "");
        Object config = store.get(LambdaKeys.functionKey(name));
        if (config == null) {
            return new ApiResponse(404, Map.of("error", "function not found: " + name));
        }
        return new ApiResponse(200, Map.of("configuration", config));
    }
}
