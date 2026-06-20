package io.cloudstub.secretsmanager;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubRequest;
import io.cloudstub.core.spi.StubResponse;
import io.cloudstub.core.spi.StubTemplates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CloudStub service module for AWS Secrets Manager.
 *
 * <p>The operation set is generated from the AWS Secrets Manager Smithy model; AWS SDK v2 drives it
 * with the JSON/X-Amz-Target protocol, so requests arrive as {@code POST /} carrying an {@code
 * X-Amz-Target: secretsmanager.<Operation>} header and a JSON body, matched by {@link
 * StubRegistrar#registerJsonTargetStub}.
 *
 * <p>The core secret operations below are <strong>state-backed</strong>: each is a {@link
 * io.cloudstub.core.spi.StubHandler} that reads and writes the shared {@link StateStore}, so a
 * secret created in one call is returned by a later {@code GetSecretValue} or {@code
 * DescribeSecret}. Each secret is stored as one entry under the {@code
 * secretsmanager/secrets/{name}} key, and its tags under {@code secretsmanager/tags/{name}} (see
 * {@link SecretsManagerKeys}). {@code TagResource}/{@code UntagResource} mutate the tag set that
 * {@code DescribeSecret} returns, and {@code BatchGetSecretValue} reads the stored values of
 * several secrets at once. {@code GetSecretValue}, {@code PutSecretValue}, {@code DescribeSecret},
 * {@code UpdateSecret}, {@code DeleteSecret}, {@code TagResource}, and {@code UntagResource} return
 * a {@code ResourceNotFoundException} (HTTP 400) for a secret that does not exist.
 *
 * <p>The remaining operations are served from static Handlebars templates in {@code
 * src/main/resources/templates/}: they return well-formed but stateless placeholder responses (e.g.
 * {@code GetRandomPassword}, {@code RotateSecret}, {@code ListSecretVersionIds}).
 *
 * <p>Not simulated: secret versioning beyond a single current version, version stages, rotation,
 * cross-region replication, resource policies, KMS encryption, recovery windows ({@code
 * DeleteSecret} removes the secret immediately), the {@code ResourceExistsException} a duplicate
 * {@code CreateSecret} would raise (it overwrites instead), and {@code BatchGetSecretValue} filters
 * (only {@code SecretIdList} is honored).
 *
 * <p>Discovered via {@code ServiceLoader} from {@code
 * META-INF/services/io.cloudstub.core.spi.CloudStubService}.
 */
public class CloudStubSecretsManagerService implements CloudStubService {

    private static final String SERVICE_ID = "secretsmanager";
    private static final String TARGET_PREFIX = "secretsmanager.";

    /** Every secret CloudStub stores has a single current version staged {@code AWSCURRENT}. */
    private static final List<String> CURRENT_STAGES = List.of("AWSCURRENT");

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudStubContext context) {
        StubRegistrar r = context.registrar();

        // State-backed operations — handlers that read and write the shared StateStore.
        r.registerJsonTargetStub(TARGET_PREFIX + "CreateSecret", this::createSecret);
        r.registerJsonTargetStub(TARGET_PREFIX + "GetSecretValue", this::getSecretValue);
        r.registerJsonTargetStub(TARGET_PREFIX + "BatchGetSecretValue", this::batchGetSecretValue);
        r.registerJsonTargetStub(TARGET_PREFIX + "PutSecretValue", this::putSecretValue);
        r.registerJsonTargetStub(TARGET_PREFIX + "DescribeSecret", this::describeSecret);
        r.registerJsonTargetStub(TARGET_PREFIX + "UpdateSecret", this::updateSecret);
        r.registerJsonTargetStub(TARGET_PREFIX + "DeleteSecret", this::deleteSecret);
        r.registerJsonTargetStub(TARGET_PREFIX + "ListSecrets", this::listSecrets);
        r.registerJsonTargetStub(TARGET_PREFIX + "TagResource", this::tagResource);
        r.registerJsonTargetStub(TARGET_PREFIX + "UntagResource", this::untagResource);

        // Template-backed operations — stateless placeholder responses.
        registerTemplate(r, "CancelRotateSecret");
        registerTemplate(r, "DeleteResourcePolicy");
        registerTemplate(r, "GetRandomPassword");
        registerTemplate(r, "GetResourcePolicy");
        registerTemplate(r, "ListSecretVersionIds");
        registerTemplate(r, "PutResourcePolicy");
        registerTemplate(r, "RemoveRegionsFromReplication");
        registerTemplate(r, "ReplicateSecretToRegions");
        registerTemplate(r, "RestoreSecret");
        registerTemplate(r, "RotateSecret");
        registerTemplate(r, "StopReplicationToReplica");
        registerTemplate(r, "UpdateSecretVersionStage");
        registerTemplate(r, "ValidateResourcePolicy");
    }

    private static void registerTemplate(StubRegistrar r, String operation) {
        r.registerJsonTargetStub(
                TARGET_PREFIX + operation,
                StubTemplates.load(CloudStubSecretsManagerService.class, operation));
    }

    private StubResponse createSecret(StubRequest req, StateStore store) {
        String name = req.jsonField("Name");
        if (name == null || name.isBlank()) {
            return invalidParameter("Invalid request: 'Name' is a required parameter.");
        }
        String versionId = UUID.randomUUID().toString();
        Map<String, String> secret = new LinkedHashMap<>();
        secret.put("arn", SecretsManagerArns.arn(name));
        secret.put("name", name);
        secret.put("secretString", nullToEmpty(req.jsonField("SecretString")));
        secret.put("description", nullToEmpty(req.jsonField("Description")));
        secret.put("versionId", versionId);
        secret.put("createdDate", String.valueOf(Instant.now().getEpochSecond()));
        synchronized (store) {
            store.put(SecretsManagerKeys.secretKey(name), secret);
            store.delete(SecretsManagerKeys.tagsKey(name));
        }
        return StubResponse.json(
                obj("ARN", secret.get("arn"), "Name", name, "VersionId", versionId));
    }

    private StubResponse getSecretValue(StubRequest req, StateStore store) {
        Map<String, String> secret = lookup(req, store);
        if (secret == null) {
            return notFound();
        }
        return StubResponse.json(secretValue(secret));
    }

    private StubResponse putSecretValue(StubRequest req, StateStore store) {
        synchronized (store) {
            Map<String, String> existing = lookup(req, store);
            if (existing == null) {
                return notFound();
            }
            // Copy-on-write: never mutate the published map in place — a lock-free reader
            // (getSecretValue/describeSecret/…) holds that same instance and would see a torn
            // update.
            Map<String, String> secret = new LinkedHashMap<>(existing);
            String versionId = UUID.randomUUID().toString();
            secret.put("secretString", nullToEmpty(req.jsonField("SecretString")));
            secret.put("versionId", versionId);
            store.put(SecretsManagerKeys.secretKey(secret.get("name")), secret);
            return StubResponse.json(
                    obj(
                            "ARN",
                            secret.get("arn"),
                            "Name",
                            secret.get("name"),
                            "VersionId",
                            versionId,
                            "VersionStages",
                            CURRENT_STAGES));
        }
    }

    private StubResponse describeSecret(StubRequest req, StateStore store) {
        Map<String, String> secret = lookup(req, store);
        if (secret == null) {
            return notFound();
        }
        return StubResponse.json(
                obj(
                        "ARN", secret.get("arn"),
                        "Name", secret.get("name"),
                        "Description", secret.get("description"),
                        "CreatedDate", createdDate(secret),
                        "Tags", tagsList(store, secret.get("name")),
                        "VersionIdsToStages", Map.of(secret.get("versionId"), CURRENT_STAGES)));
    }

    private StubResponse updateSecret(StubRequest req, StateStore store) {
        synchronized (store) {
            Map<String, String> existing = lookup(req, store);
            if (existing == null) {
                return notFound();
            }
            // Copy-on-write — see putSecretValue.
            Map<String, String> secret = new LinkedHashMap<>(existing);
            String secretString = req.jsonField("SecretString");
            if (secretString != null) {
                secret.put("secretString", secretString);
            }
            String description = req.jsonField("Description");
            if (description != null) {
                secret.put("description", description);
            }
            String versionId = UUID.randomUUID().toString();
            secret.put("versionId", versionId);
            store.put(SecretsManagerKeys.secretKey(secret.get("name")), secret);
            return StubResponse.json(
                    obj(
                            "ARN",
                            secret.get("arn"),
                            "Name",
                            secret.get("name"),
                            "VersionId",
                            versionId));
        }
    }

    private StubResponse deleteSecret(StubRequest req, StateStore store) {
        synchronized (store) {
            Map<String, String> secret = lookup(req, store);
            if (secret == null) {
                return notFound();
            }
            store.delete(SecretsManagerKeys.secretKey(secret.get("name")));
            store.delete(SecretsManagerKeys.tagsKey(secret.get("name")));
            return StubResponse.json(
                    obj(
                            "ARN", secret.get("arn"),
                            "Name", secret.get("name"),
                            "DeletionDate", Instant.now().getEpochSecond()));
        }
    }

    private StubResponse listSecrets(StubRequest req, StateStore store) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String key : store.list(SecretsManagerKeys.SECRETS_PREFIX)) {
            Map<String, String> secret = read(store, key);
            if (secret == null) {
                continue;
            }
            list.add(
                    obj(
                            "ARN", secret.get("arn"),
                            "Name", secret.get("name"),
                            "Description", secret.get("description"),
                            "CreatedDate", createdDate(secret)));
        }
        return StubResponse.json(obj("SecretList", list));
    }

    private StubResponse batchGetSecretValue(StubRequest req, StateStore store) {
        List<Map<String, Object>> values = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; ; i++) {
            String secretId = req.jsonField("SecretIdList." + i);
            if (secretId == null) {
                break;
            }
            String name = SecretsManagerArns.resolveName(secretId);
            Map<String, String> secret = read(store, SecretsManagerKeys.secretKey(name));
            if (secret == null) {
                errors.add(
                        obj(
                                "SecretId", secretId,
                                "ErrorCode", "ResourceNotFoundException",
                                "Message", "Secrets Manager can't find the specified secret."));
                continue;
            }
            values.add(secretValue(secret));
        }
        return StubResponse.json(obj("SecretValues", values, "Errors", errors));
    }

    private StubResponse tagResource(StubRequest req, StateStore store) {
        synchronized (store) {
            Map<String, String> secret = lookup(req, store);
            if (secret == null) {
                return notFound();
            }
            String name = secret.get("name");
            Map<String, String> tags = readTags(store, name);
            for (int i = 0; ; i++) {
                String key = req.jsonField("Tags." + i + ".Key");
                if (key == null) {
                    break;
                }
                tags.put(key, nullToEmpty(req.jsonField("Tags." + i + ".Value")));
            }
            store.put(SecretsManagerKeys.tagsKey(name), tags);
            return StubResponse.json("{}");
        }
    }

    private StubResponse untagResource(StubRequest req, StateStore store) {
        synchronized (store) {
            Map<String, String> secret = lookup(req, store);
            if (secret == null) {
                return notFound();
            }
            String name = secret.get("name");
            Map<String, String> tags = readTags(store, name);
            for (int i = 0; ; i++) {
                String key = req.jsonField("TagKeys." + i);
                if (key == null) {
                    break;
                }
                tags.remove(key);
            }
            if (tags.isEmpty()) {
                store.delete(SecretsManagerKeys.tagsKey(name));
            } else {
                store.put(SecretsManagerKeys.tagsKey(name), tags);
            }
            return StubResponse.json("{}");
        }
    }

    /** Reads the secret addressed by the request's {@code SecretId}, or {@code null} if absent. */
    private static Map<String, String> lookup(StubRequest req, StateStore store) {
        String name = SecretsManagerArns.resolveName(req.jsonField("SecretId"));
        if (name == null) {
            return null;
        }
        return read(store, SecretsManagerKeys.secretKey(name));
    }

    /**
     * The stored map for {@code key}, or {@code null}. The returned instance is the published one —
     * read it, but do not mutate it (a concurrent lock-free reader holds the same reference). A
     * writer copies it first; see {@code putSecretValue} and {@code readTags}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> read(StateStore store, String key) {
        Object value = store.get(key);
        return value instanceof Map ? (Map<String, String>) value : null;
    }

    /**
     * A mutable copy of the secret's tags for a writer to modify, or a fresh empty map if it has
     * none. A copy (not the stored instance) so {@code tagResource}/{@code untagResource} never
     * mutate the published map a lock-free reader holds. Never null.
     */
    private static Map<String, String> readTags(StateStore store, String name) {
        Map<String, String> tags = read(store, SecretsManagerKeys.tagsKey(name));
        return tags != null ? new LinkedHashMap<>(tags) : new LinkedHashMap<>();
    }

    /** The {@code GetSecretValue}/{@code BatchGetSecretValue} shape for a stored secret. */
    private static Map<String, Object> secretValue(Map<String, String> secret) {
        return obj(
                "ARN", secret.get("arn"),
                "Name", secret.get("name"),
                "VersionId", secret.get("versionId"),
                "SecretString", secret.get("secretString"),
                "VersionStages", CURRENT_STAGES,
                "CreatedDate", createdDate(secret));
    }

    /** A secret's tags as a list of {@code {"Key":..,"Value":..}} objects; empty if it has none. */
    private static List<Map<String, Object>> tagsList(StateStore store, String name) {
        Map<String, String> tags = read(store, SecretsManagerKeys.tagsKey(name));
        List<Map<String, Object>> out = new ArrayList<>();
        if (tags != null) {
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                out.add(obj("Key", tag.getKey(), "Value", tag.getValue()));
            }
        }
        return out;
    }

    /** The stored {@code createdDate} as a numeric epoch-seconds value for the JSON response. */
    private static Object createdDate(Map<String, String> secret) {
        return Long.parseLong(secret.get("createdDate"));
    }

    private static StubResponse notFound() {
        return error(
                "ResourceNotFoundException", "Secrets Manager can't find the specified secret.");
    }

    private static StubResponse invalidParameter(String message) {
        return error("InvalidParameterException", message);
    }

    /** An AWS JSON-protocol error response (HTTP 400) the SDK maps to the named exception type. */
    private static StubResponse error(String type, String message) {
        return StubResponse.json(400, obj("__type", type, "Message", message));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Builds an ordered JSON object from alternating key/value arguments. */
    private static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }
}
