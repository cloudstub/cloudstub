package io.cloudmock.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

@Configuration
public class AwsConfig {

    /**
     * When {@code aws.endpoint-url} is set (e.g. by CloudMock or LocalStack before the
     * Spring context starts), the clients are redirected to that address automatically.
     * In production the property is absent and the SDK uses the default regional endpoints.
     */
    @Bean
    SqsClient sqsClient(@Value("${aws.endpoint-url:}") String endpointUrl) {
        SqsClientBuilder builder = SqsClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1);
        if (!endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    SecretsManagerClient secretsManagerClient(@Value("${aws.endpoint-url:}") String endpointUrl) {
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1);
        if (!endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }
}
