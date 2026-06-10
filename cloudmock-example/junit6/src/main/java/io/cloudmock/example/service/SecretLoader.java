package io.cloudmock.example.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Stores and retrieves secrets from AWS Secrets Manager.
 */
@Service
public class SecretLoader {

    private final SecretsManagerClient client;

    public SecretLoader(SecretsManagerClient client) {
        this.client = client;
    }

    /** Creates a new secret. Returns the ARN assigned by Secrets Manager. */
    public String store(String name, String value) {
        return client.createSecret(b -> b.name(name).secretString(value)).arn();
    }

    /** Loads a secret value. Throws if the resolved value is blank. */
    public String load(String name) {
        String value = client.getSecretValue(b -> b.secretId(name)).secretString();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Secret '" + name + "' resolved to a blank value");
        }
        return value;
    }
}
