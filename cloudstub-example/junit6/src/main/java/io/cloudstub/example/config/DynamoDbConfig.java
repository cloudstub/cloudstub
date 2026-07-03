package io.cloudstub.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
class DynamoDbConfig extends AwsClientSupport {

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${aws.endpoint-url:}") String endpointUrl) {
        return build(DynamoDbClient.builder(), endpointUrl);
    }
}
