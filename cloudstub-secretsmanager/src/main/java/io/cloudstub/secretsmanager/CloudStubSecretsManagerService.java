package io.cloudstub.secretsmanager;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubRequest;
import io.cloudstub.core.spi.StubResponse;
import io.cloudstub.core.spi.StubTemplates;
import java.time.Instant;
import java.util.LinkedHashMap;
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

    private static final String NOT_FOUND_BODY =
            "{\"__type\":\"ResourceNotFoundException\",\"Message\":\"Secrets Manager can't find the"
                    + " specified secret.\"}";

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
        String versionId = UUID.randomUUID().toString();
        Map<String, String> secret = new LinkedHashMap<>();
        secret.put("arn", SecretsManagerJson.arn(name));
        secret.put("name", name);
        secret.put("secretString", nullToEmpty(req.jsonField("SecretString")));
        secret.put("description", nullToEmpty(req.jsonField("Description")));
        secret.put("versionId", versionId);
        secret.put("createdDate", String.valueOf(Instant.now().getEpochSecond()));
        store.put(SecretsManagerKeys.secretKey(name), secret);
        return StubResponse.json(
                "{\"ARN\":\""
                        + SecretsManagerJson.escape(SecretsManagerJson.arn(name))
                        + "\",\"Name\":\""
                        + SecretsManagerJson.escape(name)
                        + "\",\"VersionId\":\""
                        + versionId
                        + "\"}");
    }

    private StubResponse getSecretValue(StubRequest req, StateStore store) {
        Map<String, String> secret = lookup(req, store);
        if (secret == null) {
            return notFound();
        }
        return StubResponse.json(
                "{\"ARN\":\""
                        + SecretsManagerJson.escape(secret.get("arn"))
                        + "\",\"Name\":\""
                        + SecretsManagerJson.escape(secret.get("name"))
                        + "\",\"VersionId\":\""
                        + secret.get("versionId")
                        + "\",\"SecretString\":\""
                        + SecretsManagerJson.escape(secret.get("secretString"))
                        + "\",\"VersionStages\":[\"AWSCURRENT\"],\"CreatedDate\":"
                        + secret.get("createdDate")
                        + "}");
    }

    private StubResponse putSecretValue(StubRequest req, StateStore store) {
        Map<String, String> secret = lookup(req, store);
        if (secret == null) {
            return notFound();
        }
        String versionId = UUID.randomUUID().toString();
        secret.put("secretString", nullToEmpty(req.jsonField("SecretString")));
        secret.put("versionId", versionId);
        store.put(SecretsManagerKeys.secretKey(secret.get("name")), secret);
        return StubResponse.json(
                "{\"ARN\":\""
                        + SecretsManagerJson.escape(secret.get("arn"))
                        + "\",\"Name\":\""
                        + SecretsManagerJson.escape(secret.get("name"))
                        + "\",\"VersionId\":\""
                        + versionId
                        + "\",\"VersionStages\":[\"AWSCURRENT\"]}");
    }

    private StubResponse describeSecret(StubRequest req, StateStore store) {
        Map<String, String> secret = lookup(req, store);
        if (secret == null) {
            return notFound();
        }
        return StubResponse.json(
                "{\"ARN\":\""
                        + SecretsManagerJson.escape(secret.get("arn"))
                        + "\",\"Name\":\""
                        + SecretsManagerJson.escape(secret.get("name"))
                        + "\",\"Description\":\""
                        + SecretsManagerJson.escape(secret.get("description"))
                        + "\",\"CreatedDate\":"
                        + secret.get("createdDate")
                        + ",\"Tags\":"
                        + tagsJson(store, secret.get("name"))
                        + ",\"VersionIdsToStages\":{\""
                        + secret.get("versionId")
                        + "\":[\"AWSCURRENT\"]}}");
    }

    private StubResponse updateSecret(StubRequest req, StateStore store) {
        Map<String, String> secret = lookup(req, store);
        if (secret == null) {
            return notFound();
        }
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
                "{\"ARN\":\""
                        + SecretsManagerJson.escape(secret.get("arn"))
                        + "\",\"Name\":\""
                        + SecretsManagerJson.escape(secret.get("name"))
                        + "\",\"VersionId\":\""
                        + versionId
                        + "\"}");
    }

    private StubResponse deleteSecret(StubRequest req, StateStore store) {
        Map<String, String> secret = lookup(req, store);
        if (secret == null) {
            return notFound();
        }
        store.delete(SecretsManagerKeys.secretKey(secret.get("name")));
        store.delete(SecretsManagerKeys.tagsKey(secret.get("name")));
        return StubResponse.json(
                "{\"ARN\":\""
                        + SecretsManagerJson.escape(secret.get("arn"))
                        + "\",\"Name\":\""
                        + SecretsManagerJson.escape(secret.get("name"))
                        + "\",\"DeletionDate\":"
                        + Instant.now().getEpochSecond()
                        + "}");
    }

    private StubResponse listSecrets(StubRequest req, StateStore store) {
        StringBuilder list = new StringBuilder();
        int count = 0;
        for (String key : store.list(SecretsManagerKeys.SECRETS_PREFIX)) {
            Map<String, String> secret = read(store, key);
            if (secret == null) {
                continue;
            }
            if (count > 0) {
                list.append(',');
            }
            list.append("{\"ARN\":\"")
                    .append(SecretsManagerJson.escape(secret.get("arn")))
                    .append("\",\"Name\":\"")
                    .append(SecretsManagerJson.escape(secret.get("name")))
                    .append("\",\"Description\":\"")
                    .append(SecretsManagerJson.escape(secret.get("description")))
                    .append("\",\"CreatedDate\":")
                    .append(secret.get("createdDate"))
                    .append('}');
            count++;
        }
        return StubResponse.json("{\"SecretList\":[" + list + "]}");
    }

    private StubResponse batchGetSecretValue(StubRequest req, StateStore store) {
        StringBuilder values = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        int valueCount = 0;
        int errorCount = 0;
        for (int i = 0; ; i++) {
            String secretId = req.jsonField("SecretIdList." + i);
            if (secretId == null) {
                break;
            }
            String name = SecretsManagerJson.resolveName(secretId);
            Map<String, String> secret = read(store, SecretsManagerKeys.secretKey(name));
            if (secret == null) {
                if (errorCount > 0) {
                    errors.append(',');
                }
                errors.append("{\"SecretId\":\"")
                        .append(SecretsManagerJson.escape(secretId))
                        .append("\",\"ErrorCode\":\"ResourceNotFoundException\",\"Message\":\"")
                        .append("Secrets Manager can't find the specified secret.\"}");
                errorCount++;
                continue;
            }
            if (valueCount > 0) {
                values.append(',');
            }
            values.append("{\"ARN\":\"")
                    .append(SecretsManagerJson.escape(secret.get("arn")))
                    .append("\",\"Name\":\"")
                    .append(SecretsManagerJson.escape(secret.get("name")))
                    .append("\",\"VersionId\":\"")
                    .append(secret.get("versionId"))
                    .append("\",\"SecretString\":\"")
                    .append(SecretsManagerJson.escape(secret.get("secretString")))
                    .append("\",\"VersionStages\":[\"AWSCURRENT\"],\"CreatedDate\":")
                    .append(secret.get("createdDate"))
                    .append('}');
            valueCount++;
        }
        return StubResponse.json("{\"SecretValues\":[" + values + "],\"Errors\":[" + errors + "]}");
    }

    private StubResponse tagResource(StubRequest req, StateStore store) {
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

    private StubResponse untagResource(StubRequest req, StateStore store) {
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

    /** Reads the secret addressed by the request's {@code SecretId}, or {@code null} if absent. */
    private static Map<String, String> lookup(StubRequest req, StateStore store) {
        String name = SecretsManagerJson.resolveName(req.jsonField("SecretId"));
        if (name == null) {
            return null;
        }
        return read(store, SecretsManagerKeys.secretKey(name));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> read(StateStore store, String key) {
        Object value = store.get(key);
        return value instanceof Map ? (Map<String, String>) value : null;
    }

    /** The secret's tags, or a fresh empty map if it has none. Never null. */
    private static Map<String, String> readTags(StateStore store, String name) {
        Map<String, String> tags = read(store, SecretsManagerKeys.tagsKey(name));
        return tags != null ? tags : new LinkedHashMap<>();
    }

    /** Renders a secret's tags as a JSON array of {@code {"Key":..,"Value":..}} objects. */
    private static String tagsJson(StateStore store, String name) {
        Map<String, String> tags = read(store, SecretsManagerKeys.tagsKey(name));
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append("{\"Key\":\"")
                    .append(SecretsManagerJson.escape(tag.getKey()))
                    .append("\",\"Value\":\"")
                    .append(SecretsManagerJson.escape(tag.getValue()))
                    .append("\"}");
            first = false;
        }
        return sb.append(']').toString();
    }

    private static StubResponse notFound() {
        return StubResponse.of(400, StubResponse.CONTENT_TYPE_JSON, NOT_FOUND_BODY);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
