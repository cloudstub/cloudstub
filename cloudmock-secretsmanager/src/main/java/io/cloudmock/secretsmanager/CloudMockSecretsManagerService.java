package io.cloudmock.secretsmanager;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;

/**
 * CloudMock service module for AWS Secrets Manager.
 *
 * <p>Registers stateless JSON/X-Amz-Target stubs for the core Secrets Manager operations.
 * Requests arrive as {@code POST /} with header {@code X-Amz-Target: secretsmanager.<Operation>}.
 * Responses are well-formed JSON that {@code SecretsManagerClient} (AWS SDK v2) parses without
 * error. Secret state is not simulated — {@code GetSecretValue} always returns a fixed
 * {@code SecretString} regardless of what was passed to {@code CreateSecret}.
 *
 * <p>Discovered via {@code ServiceLoader} from
 * {@code META-INF/services/io.cloudmock.core.spi.CloudMockService}.
 */
public class CloudMockSecretsManagerService implements CloudMockService {

    private static final String SERVICE_ID = "secretsmanager";
    private static final String PREFIX     = SERVICE_ID + ".";

    private static final String ARN_PREFIX =
            "arn:aws:secretsmanager:us-east-1:000000000000:secret:";

    // {{jsonPath request.body '$.Name'}} echoes the secret name from the request.
    // {{randomValue type='UUID'}} generates a fresh version ID per request.

    private static final String CREATE_SECRET =
            """
            {"ARN":"%s{{jsonPath request.body '$.Name'}}","Name":"{{jsonPath request.body '$.Name'}}","VersionId":"{{randomValue type='UUID'}}"}"""
            .formatted(ARN_PREFIX);

    private static final String GET_SECRET_VALUE =
            """
            {"ARN":"%s{{jsonPath request.body '$.SecretId'}}","Name":"{{jsonPath request.body '$.SecretId'}}","SecretString":"{\\"username\\":\\"test\\",\\"password\\":\\"test\\"}","VersionId":"{{randomValue type='UUID'}}"}"""
            .formatted(ARN_PREFIX);

    private static final String PUT_SECRET_VALUE =
            """
            {"ARN":"%s{{jsonPath request.body '$.SecretId'}}","Name":"{{jsonPath request.body '$.SecretId'}}","VersionId":"{{randomValue type='UUID'}}"}"""
            .formatted(ARN_PREFIX);

    private static final String DELETE_SECRET =
            """
            {"ARN":"%s{{jsonPath request.body '$.SecretId'}}","Name":"{{jsonPath request.body '$.SecretId'}}","DeletionDate":1.0}"""
            .formatted(ARN_PREFIX);

    private static final String LIST_SECRETS =
            """
            {"SecretList":[{"ARN":"%scloudmock-secret","Name":"cloudmock-secret"}]}"""
            .formatted(ARN_PREFIX);

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudMockContext context) {
        StubRegistrar registrar = context.registrar();
        registrar.registerJsonTargetStub(PREFIX + "CreateSecret",    CREATE_SECRET);
        registrar.registerJsonTargetStub(PREFIX + "GetSecretValue",  GET_SECRET_VALUE);
        registrar.registerJsonTargetStub(PREFIX + "PutSecretValue",  PUT_SECRET_VALUE);
        registrar.registerJsonTargetStub(PREFIX + "DeleteSecret",    DELETE_SECRET);
        registrar.registerJsonTargetStub(PREFIX + "ListSecrets",     LIST_SECRETS);
    }
}
