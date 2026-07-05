package io.cloudstub.lambda;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.Json;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubRequest;
import io.cloudstub.core.spi.StubResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudStub service module for AWS Lambda.
 *
 * <p>Lambda uses the REST JSON protocol (not the {@code X-Amz-Target} JSON protocol): AWS SDK v2
 * drives it with HTTP method + path, e.g. {@code POST /2015-03-31/functions} and {@code POST
 * /2015-03-31/functions/{name}/invocations}, matched by {@link StubRegistrar#registerRestStub}.
 *
 * <p>The function lifecycle and tag operations are <strong>state-backed</strong>: each is a {@link
 * io.cloudstub.core.spi.StubHandler} that reads and writes the shared {@link StateStore}, so a
 * function created by {@code CreateFunction} is returned by a later {@code GetFunction} and
 * reflected in {@code ListFunctions}. State is keyed under the {@code lambda/} prefix (see {@link
 * LambdaKeys}):
 *
 * <ul>
 *   <li>{@code lambda/functions/{name}} → the function configuration
 *   <li>{@code lambda/tags/{name}} → the function's tag set
 * </ul>
 *
 * <p>{@code Invoke} does not execute code: it echoes the request payload back as the response body
 * with status {@code 200} and {@code X-Amz-Executed-Version: $LATEST}, so callers get a
 * deterministic, inspectable result.
 *
 * <p>Not simulated: function execution, versions and aliases (a qualifier in the name or ARN is
 * ignored), layers, concurrency, event source mappings, URLs, event-invoke config, and durable
 * executions. {@code CodeSize}/{@code CodeSha256} are derived from the inline {@code ZipFile} bytes
 * only.
 *
 * <p>Discovered via {@code ServiceLoader} from {@code
 * META-INF/services/io.cloudstub.core.spi.CloudStubService}.
 */
public class CloudStubLambdaService implements CloudStubService {

    private static final String SERVICE_ID = "lambda";
    private static final String FUNCTIONS = "/2015-03-31/functions/";
    private static final String TAGS = "/2017-03-31/tags/";
    private static final String QUERY = "(\\?.*)?";

    // Serialises the read-modify-write mutating handlers so concurrent requests on one function
    // cannot lose an update.
    private final Object writeLock = new Object();

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudStubContext context) {
        StubRegistrar r = context.registrar();

        r.registerRestStub(
                HttpMethod.POST, "/2015-03-31/functions/?" + QUERY, this::createFunction);
        r.registerRestStub(HttpMethod.GET, "/2015-03-31/functions/?" + QUERY, this::listFunctions);
        r.registerRestStub(
                HttpMethod.GET, "/2015-03-31/functions/[^/?]+" + QUERY, this::getFunction);
        r.registerRestStub(
                HttpMethod.DELETE, "/2015-03-31/functions/[^/?]+" + QUERY, this::deleteFunction);
        r.registerRestStub(
                HttpMethod.GET,
                "/2015-03-31/functions/[^/?]+/configuration" + QUERY,
                this::getFunctionConfiguration);
        r.registerRestStub(
                HttpMethod.PUT,
                "/2015-03-31/functions/[^/?]+/configuration" + QUERY,
                this::updateFunctionConfiguration);
        r.registerRestStub(
                HttpMethod.PUT,
                "/2015-03-31/functions/[^/?]+/code" + QUERY,
                this::updateFunctionCode);
        r.registerRestStub(
                HttpMethod.POST, "/2015-03-31/functions/[^/?]+/invocations" + QUERY, this::invoke);

        // Tags.
        r.registerRestStub(HttpMethod.GET, TAGS + ".+", this::listTags);
        r.registerRestStub(HttpMethod.POST, TAGS + ".+", this::tagResource);
        r.registerRestStub(HttpMethod.DELETE, TAGS + ".+", this::untagResource);

        // Account settings.
        r.registerRestStub(
                HttpMethod.GET, "/2016-08-19/account-settings/?" + QUERY, this::getAccountSettings);
    }

    // ---- Function lifecycle -------------------------------------------------

    private StubResponse createFunction(StubRequest req, StateStore store) {
        Map<String, Object> body = LambdaJson.parseObject(req.body());
        String name = str(body.get("FunctionName"));
        if (name == null || name.isBlank()) {
            return error(400, "InvalidParameterValueException", "FunctionName is required.");
        }
        synchronized (writeLock) {
            if (store.get(LambdaKeys.functionKey(name)) != null) {
                return error(409, "ResourceConflictException", "Function already exist: " + name);
            }
            Map<String, Object> config = functionConfiguration(name, body, null);
            applyCode(config, inlineZip(body));
            store.put(LambdaKeys.functionKey(name), config);
            Map<String, Object> tags = asMap(body.get("Tags"));
            if (tags != null && !tags.isEmpty()) {
                store.put(LambdaKeys.tagsKey(name), new LinkedHashMap<>(tags));
            }
            return StubResponse.json(201, config);
        }
    }

    private StubResponse getFunction(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), FUNCTIONS));
        Map<String, Object> config = config(store, name);
        if (config == null) {
            return notFound(name);
        }
        Map<String, Object> tags = asMap(store.get(LambdaKeys.tagsKey(name)));
        return StubResponse.json(
                Json.object(
                        "Configuration",
                        config,
                        "Code",
                        Json.object(
                                "RepositoryType",
                                "S3",
                                "Location",
                                "https://cloudstub.local/" + name + ".zip"),
                        "Tags",
                        tags != null ? tags : Map.of()));
    }

    private StubResponse getFunctionConfiguration(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), FUNCTIONS));
        Map<String, Object> config = config(store, name);
        return config == null ? notFound(name) : StubResponse.json(config);
    }

    private StubResponse listFunctions(StubRequest req, StateStore store) {
        List<Map<String, Object>> functions = new ArrayList<>();
        for (String key : store.list(LambdaKeys.FUNCTIONS_PREFIX)) {
            Map<String, Object> config = asMap(store.get(key));
            if (config != null) {
                functions.add(config);
            }
        }
        return StubResponse.json(Json.object("Functions", functions));
    }

    private StubResponse deleteFunction(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), FUNCTIONS));
        synchronized (writeLock) {
            if (config(store, name) == null) {
                return notFound(name);
            }
            store.delete(LambdaKeys.functionKey(name));
            store.delete(LambdaKeys.tagsKey(name));
            return StubResponse.of(204, "application/json", "");
        }
    }

    private StubResponse updateFunctionConfiguration(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), FUNCTIONS));
        Map<String, Object> body = LambdaJson.parseObject(req.body());
        synchronized (writeLock) {
            Map<String, Object> existing = config(store, name);
            if (existing == null) {
                return notFound(name);
            }
            Map<String, Object> config = functionConfiguration(name, body, existing);
            store.put(LambdaKeys.functionKey(name), config);
            return StubResponse.json(config);
        }
    }

    private StubResponse updateFunctionCode(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), FUNCTIONS));
        Map<String, Object> body = LambdaJson.parseObject(req.body());
        synchronized (writeLock) {
            Map<String, Object> existing = config(store, name);
            if (existing == null) {
                return notFound(name);
            }
            Map<String, Object> config = new LinkedHashMap<>(existing);
            applyCode(config, str(body.get("ZipFile")));
            config.put("LastModified", Instant.now().toString());
            store.put(LambdaKeys.functionKey(name), config);
            return StubResponse.json(config);
        }
    }

    private StubResponse invoke(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), FUNCTIONS));
        if (config(store, name) == null) {
            return notFound(name);
        }
        String invocationType = req.header("X-Amz-Invocation-Type");
        if ("Event".equalsIgnoreCase(invocationType)) {
            return StubResponse.of(202, "application/json", "")
                    .withHeader("X-Amz-Executed-Version", "$LATEST");
        }
        if ("DryRun".equalsIgnoreCase(invocationType)) {
            return StubResponse.of(204, "application/json", "")
                    .withHeader("X-Amz-Executed-Version", "$LATEST");
        }
        String payload = req.body() == null ? "" : req.body();
        return StubResponse.of(200, "application/json", payload)
                .withHeader("X-Amz-Executed-Version", "$LATEST");
    }

    // ---- Tags ---------------------------------------------------------------

    private StubResponse listTags(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), TAGS));
        Map<String, Object> tags = asMap(store.get(LambdaKeys.tagsKey(name)));
        return StubResponse.json(Json.object("Tags", tags != null ? tags : Map.of()));
    }

    private StubResponse tagResource(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), TAGS));
        Map<String, Object> body = LambdaJson.parseObject(req.body());
        Map<String, Object> incoming = asMap(body.get("Tags"));
        synchronized (writeLock) {
            Map<String, Object> existing = asMap(store.get(LambdaKeys.tagsKey(name)));
            Map<String, Object> tags =
                    existing != null ? new LinkedHashMap<>(existing) : new LinkedHashMap<>();
            if (incoming != null) {
                tags.putAll(incoming);
            }
            store.put(LambdaKeys.tagsKey(name), tags);
        }
        return StubResponse.of(204, "application/json", "");
    }

    private StubResponse untagResource(StubRequest req, StateStore store) {
        String name = LambdaHelpers.functionName(pathToken(req.path(), TAGS));
        List<String> keys = req.queryParamValues("tagKeys");
        synchronized (writeLock) {
            Map<String, Object> existing = asMap(store.get(LambdaKeys.tagsKey(name)));
            if (existing != null) {
                Map<String, Object> tags = new LinkedHashMap<>(existing);
                keys.forEach(tags::remove);
                store.put(LambdaKeys.tagsKey(name), tags);
            }
        }
        return StubResponse.of(204, "application/json", "");
    }

    private StubResponse getAccountSettings(StubRequest req, StateStore store) {
        return StubResponse.json(
                Json.object(
                        "AccountLimit",
                                Json.object(
                                        "TotalCodeSize", 80530636800L,
                                        "CodeSizeUnzipped", 262144000L,
                                        "CodeSizeZipped", 52428800L,
                                        "ConcurrentExecutions", 1000L,
                                        "UnreservedConcurrentExecutions", 1000L),
                        "AccountUsage",
                                Json.object(
                                        "TotalCodeSize",
                                        codeSizeUsed(store),
                                        "FunctionCount",
                                        (long) store.list(LambdaKeys.FUNCTIONS_PREFIX).size())));
    }

    // ---- Config building ----------------------------------------------------

    private Map<String, Object> functionConfiguration(
            String name, Map<String, Object> body, Map<String, Object> existing) {
        Map<String, Object> config =
                existing != null ? new LinkedHashMap<>(existing) : new LinkedHashMap<>();
        config.put("FunctionName", name);
        config.put("FunctionArn", LambdaHelpers.arn(name));
        copyIfPresent(config, body, "Runtime");
        copyIfPresent(config, body, "Role");
        copyIfPresent(config, body, "Handler");
        copyIfPresent(config, body, "Description");
        copyIfPresent(config, body, "Timeout");
        copyIfPresent(config, body, "MemorySize");
        copyIfPresent(config, body, "Environment");
        copyIfPresent(config, body, "PackageType");
        copyIfPresent(config, body, "Architectures");
        config.putIfAbsent("Description", "");
        config.putIfAbsent("Timeout", 3L);
        config.putIfAbsent("MemorySize", 128L);
        config.putIfAbsent("PackageType", "Zip");
        config.put("Version", "$LATEST");
        config.put("State", "Active");
        config.put("LastUpdateStatus", "Successful");
        config.put("LastModified", Instant.now().toString());
        config.putIfAbsent("CodeSize", 0L);
        config.putIfAbsent("CodeSha256", "");
        return config;
    }

    /**
     * Recomputes {@code CodeSize}/{@code CodeSha256} from the deployment package. The inline {@code
     * ZipFile} arrives base64-encoded, so it is decoded first and the size and digest describe the
     * decoded zip bytes. A value that is not valid base64 is hashed as-is.
     */
    private void applyCode(Map<String, Object> config, String zipFileBase64) {
        if (zipFileBase64 == null) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(zipFileBase64);
        } catch (IllegalArgumentException e) {
            bytes = zipFileBase64.getBytes(StandardCharsets.UTF_8);
        }
        config.put("CodeSize", (long) bytes.length);
        config.put("CodeSha256", LambdaHelpers.sha256Base64(bytes));
    }

    private static String inlineZip(Map<String, Object> body) {
        Map<String, Object> code = asMap(body.get("Code"));
        return code == null ? null : str(code.get("ZipFile"));
    }

    private long codeSizeUsed(StateStore store) {
        long total = 0;
        for (String key : store.list(LambdaKeys.FUNCTIONS_PREFIX)) {
            Map<String, Object> config = asMap(store.get(key));
            if (config != null && config.get("CodeSize") instanceof Number size) {
                total += size.longValue();
            }
        }
        return total;
    }

    // ---- Helpers ------------------------------------------------------------

    private static Map<String, Object> config(StateStore store, String name) {
        return name == null ? null : asMap(store.get(LambdaKeys.functionKey(name)));
    }

    /** The path segment following {@code prefix}, up to the next {@code '/'}. */
    private static String pathToken(String path, String prefix) {
        int start = path.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        int from = start + prefix.length();
        int slash = path.indexOf('/', from);
        return slash < 0 ? path.substring(from) : path.substring(from, slash);
    }

    private static void copyIfPresent(
            Map<String, Object> target, Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value != null) {
            target.put(field, value);
        }
    }

    private StubResponse notFound(String name) {
        return error(404, "ResourceNotFoundException", "Function not found: " + name);
    }

    private StubResponse error(int status, String type, String message) {
        return StubResponse.json(status, Json.object("Type", "User", "Message", message))
                .withHeader("X-Amzn-Errortype", type);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }
}
