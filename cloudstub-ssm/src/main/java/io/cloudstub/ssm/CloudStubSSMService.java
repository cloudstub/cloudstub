package io.cloudstub.ssm;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.Json;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubRequest;
import io.cloudstub.core.spi.StubResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudStub service module for the Amazon SSM Parameter Store.
 *
 * <p>AWS SDK v2 drives SSM with the JSON/X-Amz-Target protocol, so requests carry an {@code
 * X-Amz-Target} header (e.g. {@code AmazonSSM.PutParameter}) and a JSON body, matched by {@link
 * StubRegistrar#registerJsonTargetStub}.
 *
 * <p>The Parameter Store operations are <strong>state-backed</strong>: each is a {@link
 * io.cloudstub.core.spi.StubHandler} that reads and writes the shared {@link StateStore}, so a
 * parameter written by {@code PutParameter} is returned by a later {@code GetParameter}. State is
 * keyed under the {@code ssm/} prefix (see {@link SsmKeys}). {@code PutParameter} assigns version 1
 * on create and increments the version on an overwrite.
 *
 * <p>Registered operations: {@code PutParameter}, {@code GetParameter}, {@code GetParameters},
 * {@code GetParametersByPath}, {@code DeleteParameter}, {@code DeleteParameters}, {@code
 * DescribeParameters}, {@code GetParameterHistory}, {@code LabelParameterVersion}, {@code
 * AddTagsToResource}, {@code RemoveTagsFromResource}, and {@code ListTagsForResource}. Operations
 * outside the Parameter Store surface are not registered and return HTTP 404.
 *
 * <p>Not simulated: KMS encryption (a {@code SecureString} value is stored and returned in
 * plaintext, regardless of {@code WithDecryption}), parameter policies, tiers (all reported as
 * {@code Standard}), version selectors and labels in a {@code GetParameter} name, {@code
 * ParameterFilters} on {@code GetParametersByPath}/{@code DescribeParameters}, pagination, and
 * per-version history (only the current version is retained).
 *
 * <p>Discovered via {@code ServiceLoader} from {@code
 * META-INF/services/io.cloudstub.core.spi.CloudStubService}.
 */
public class CloudStubSSMService implements CloudStubService {

    private static final String SERVICE_ID = "ssm";
    private static final String TARGET_PREFIX = "AmazonSSM.";

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudStubContext context) {
        StubRegistrar r = context.registrar();
        r.registerJsonTargetStub(TARGET_PREFIX + "PutParameter", this::putParameter);
        r.registerJsonTargetStub(TARGET_PREFIX + "GetParameter", this::getParameter);
        r.registerJsonTargetStub(TARGET_PREFIX + "GetParameters", this::getParameters);
        r.registerJsonTargetStub(TARGET_PREFIX + "GetParametersByPath", this::getParametersByPath);
        r.registerJsonTargetStub(TARGET_PREFIX + "DeleteParameter", this::deleteParameter);
        r.registerJsonTargetStub(TARGET_PREFIX + "DeleteParameters", this::deleteParameters);
        r.registerJsonTargetStub(TARGET_PREFIX + "DescribeParameters", this::describeParameters);
        r.registerJsonTargetStub(TARGET_PREFIX + "GetParameterHistory", this::getParameterHistory);
        r.registerJsonTargetStub(
                TARGET_PREFIX + "LabelParameterVersion", this::labelParameterVersion);
        r.registerJsonTargetStub(TARGET_PREFIX + "AddTagsToResource", this::addTagsToResource);
        r.registerJsonTargetStub(
                TARGET_PREFIX + "RemoveTagsFromResource", this::removeTagsFromResource);
        r.registerJsonTargetStub(TARGET_PREFIX + "ListTagsForResource", this::listTagsForResource);
    }

    private StubResponse putParameter(StubRequest req, StateStore store) {
        String name = req.jsonField("Name");
        if (name == null || name.isBlank()) {
            return error("ValidationException", "Parameter name is required.");
        }
        boolean overwrite = Boolean.parseBoolean(req.jsonField("Overwrite"));
        synchronized (store) {
            Map<String, String> existing = SsmParameters.read(store, SsmKeys.parameterKey(name));
            if (existing != null && !overwrite) {
                return error(
                        "ParameterAlreadyExists",
                        "The parameter already exists. To overwrite this value, set the overwrite"
                                + " option in the request to true.");
            }
            Map<String, String> parameter =
                    SsmParameters.newVersion(
                            existing,
                            name,
                            req.jsonField("Value"),
                            req.jsonField("Type"),
                            req.jsonField("DataType"),
                            req.jsonField("Description"));
            store.put(SsmKeys.parameterKey(name), parameter);
            putTags(store, name, req, "Tags");
            return StubResponse.json(
                    Json.object("Version", SsmParameters.version(parameter), "Tier", "Standard"));
        }
    }

    private StubResponse getParameter(StubRequest req, StateStore store) {
        String name = req.jsonField("Name");
        Map<String, String> parameter = SsmParameters.read(store, SsmKeys.parameterKey(name));
        if (parameter == null) {
            return parameterNotFound(name);
        }
        return StubResponse.json(Json.object("Parameter", SsmParameters.parameterShape(parameter)));
    }

    private StubResponse getParameters(StubRequest req, StateStore store) {
        List<Map<String, Object>> found = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String name : stringList(req, "Names")) {
            Map<String, String> parameter = SsmParameters.read(store, SsmKeys.parameterKey(name));
            if (parameter == null) {
                invalid.add(name);
            } else {
                found.add(SsmParameters.parameterShape(parameter));
            }
        }
        return StubResponse.json(Json.object("Parameters", found, "InvalidParameters", invalid));
    }

    private StubResponse getParametersByPath(StubRequest req, StateStore store) {
        String path = req.jsonField("Path");
        boolean recursive = Boolean.parseBoolean(req.jsonField("Recursive"));
        List<Map<String, Object>> found = new ArrayList<>();
        if (path != null) {
            for (String key : store.list(SsmKeys.PARAMETERS_PREFIX)) {
                String name = SsmKeys.nameFromKey(key);
                if (!underPath(name, path, recursive)) {
                    continue;
                }
                Map<String, String> parameter = SsmParameters.read(store, key);
                if (parameter != null) {
                    found.add(SsmParameters.parameterShape(parameter));
                }
            }
        }
        return StubResponse.json(Json.object("Parameters", found));
    }

    private StubResponse deleteParameter(StubRequest req, StateStore store) {
        String name = req.jsonField("Name");
        synchronized (store) {
            if (SsmParameters.read(store, SsmKeys.parameterKey(name)) == null) {
                return parameterNotFound(name);
            }
            store.delete(SsmKeys.parameterKey(name));
            store.delete(SsmKeys.tagsKey(name));
        }
        return StubResponse.json("{}");
    }

    private StubResponse deleteParameters(StubRequest req, StateStore store) {
        List<String> deleted = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        synchronized (store) {
            for (String name : stringList(req, "Names")) {
                if (SsmParameters.read(store, SsmKeys.parameterKey(name)) == null) {
                    invalid.add(name);
                } else {
                    store.delete(SsmKeys.parameterKey(name));
                    store.delete(SsmKeys.tagsKey(name));
                    deleted.add(name);
                }
            }
        }
        return StubResponse.json(
                Json.object("DeletedParameters", deleted, "InvalidParameters", invalid));
    }

    private StubResponse describeParameters(StubRequest req, StateStore store) {
        List<Map<String, Object>> metadata = new ArrayList<>();
        for (String key : store.list(SsmKeys.PARAMETERS_PREFIX)) {
            Map<String, String> parameter = SsmParameters.read(store, key);
            if (parameter != null) {
                metadata.add(SsmParameters.metadataShape(parameter));
            }
        }
        return StubResponse.json(Json.object("Parameters", metadata));
    }

    private StubResponse getParameterHistory(StubRequest req, StateStore store) {
        String name = req.jsonField("Name");
        Map<String, String> parameter = SsmParameters.read(store, SsmKeys.parameterKey(name));
        if (parameter == null) {
            return parameterNotFound(name);
        }
        return StubResponse.json(
                Json.object("Parameters", List.of(SsmParameters.historyShape(parameter))));
    }

    private StubResponse labelParameterVersion(StubRequest req, StateStore store) {
        String name = req.jsonField("Name");
        Map<String, String> parameter = SsmParameters.read(store, SsmKeys.parameterKey(name));
        if (parameter == null) {
            return parameterNotFound(name);
        }
        long current = SsmParameters.version(parameter);
        String requested = req.jsonField("ParameterVersion");
        long version = requested != null ? SsmParameters.parseLong(requested) : current;
        if (version != current) {
            return error(
                    "ParameterVersionNotFound",
                    "Version " + version + " of parameter " + name + " not found.");
        }
        return StubResponse.json(
                Json.object("InvalidLabels", List.of(), "ParameterVersion", version));
    }

    private StubResponse addTagsToResource(StubRequest req, StateStore store) {
        String resourceId = req.jsonField("ResourceId");
        synchronized (store) {
            if (!parameterExists(store, resourceId)) {
                return invalidResourceId(resourceId);
            }
            putTags(store, resourceId, req, "Tags");
        }
        return StubResponse.json("{}");
    }

    private StubResponse removeTagsFromResource(StubRequest req, StateStore store) {
        String resourceId = req.jsonField("ResourceId");
        synchronized (store) {
            if (!parameterExists(store, resourceId)) {
                return invalidResourceId(resourceId);
            }
            Map<String, String> tags = readTags(store, resourceId);
            for (String key : stringList(req, "TagKeys")) {
                tags.remove(key);
            }
            if (tags.isEmpty()) {
                store.delete(SsmKeys.tagsKey(resourceId));
            } else {
                store.put(SsmKeys.tagsKey(resourceId), tags);
            }
        }
        return StubResponse.json("{}");
    }

    private StubResponse listTagsForResource(StubRequest req, StateStore store) {
        String resourceId = req.jsonField("ResourceId");
        if (!parameterExists(store, resourceId)) {
            return invalidResourceId(resourceId);
        }
        return StubResponse.json(Json.object("TagList", tagList(store, resourceId)));
    }

    /** Whether a parameter named {@code resourceId} exists. Tags target only the Parameter type. */
    private static boolean parameterExists(StateStore store, String resourceId) {
        return resourceId != null
                && SsmParameters.read(store, SsmKeys.parameterKey(resourceId)) != null;
    }

    /**
     * Reads {@code Tags.i.Key}/{@code Tags.i.Value} (or the named field) from the request and
     * merges them into the resource's stored tags. Must be called while holding the store lock.
     */
    private static void putTags(StateStore store, String name, StubRequest req, String field) {
        if (req.jsonField(field + ".0.Key") == null) {
            // No tags in the request; leave any existing tags untouched.
            return;
        }
        Map<String, String> tags = readTags(store, name);
        for (int i = 0; ; i++) {
            String key = req.jsonField(field + "." + i + ".Key");
            if (key == null) {
                break;
            }
            tags.put(key, SsmParameters.nullToEmpty(req.jsonField(field + "." + i + ".Value")));
        }
        store.put(SsmKeys.tagsKey(name), tags);
    }

    /** The tags of a resource as a list of {@code {"Key":..,"Value":..}} objects. */
    private static List<Map<String, Object>> tagList(StateStore store, String name) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, String> tags = SsmParameters.read(store, SsmKeys.tagsKey(name));
        if (tags != null) {
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                out.add(Json.object("Key", tag.getKey(), "Value", tag.getValue()));
            }
        }
        return out;
    }

    /** Reads the scalar list {@code field.0}, {@code field.1}, ... until the first gap. */
    private static List<String> stringList(StubRequest req, String field) {
        List<String> values = new ArrayList<>();
        for (int i = 0; ; i++) {
            String value = req.jsonField(field + "." + i);
            if (value == null) {
                break;
            }
            values.add(value);
        }
        return values;
    }

    /**
     * A mutable copy of a resource's tags for a writer to modify, or a fresh empty map. A copy, not
     * the stored instance, so a concurrent lock-free reader never sees a torn update.
     */
    private static Map<String, String> readTags(StateStore store, String name) {
        Map<String, String> tags = SsmParameters.read(store, SsmKeys.tagsKey(name));
        return tags != null ? new LinkedHashMap<>(tags) : new LinkedHashMap<>();
    }

    /**
     * Whether {@code name} lies under the hierarchy {@code path}: a direct child when not
     * recursive, any descendant when recursive.
     */
    private static boolean underPath(String name, String path, boolean recursive) {
        String prefix = path.endsWith("/") ? path : path + "/";
        if (!name.startsWith(prefix)) {
            return false;
        }
        return recursive || name.indexOf('/', prefix.length()) < 0;
    }

    private static StubResponse parameterNotFound(String name) {
        return error("ParameterNotFound", "Parameter " + name + " not found.");
    }

    private static StubResponse invalidResourceId(String resourceId) {
        return error("InvalidResourceId", "Resource " + resourceId + " does not exist.");
    }

    /** An AWS JSON-protocol error response (HTTP 400) the SDK maps to the named exception type. */
    private static StubResponse error(String type, String message) {
        return StubResponse.json(400, Json.object("__type", type, "message", message));
    }
}
