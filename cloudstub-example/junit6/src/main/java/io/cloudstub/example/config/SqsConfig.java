package io.cloudstub.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
class SqsConfig extends AwsClientSupport {

    @Bean
    SqsClient sqsClient(@Value("${aws.endpoint-url:}") String endpointUrl) {
        return build(SqsClient.builder(), endpointUrl);
    }
}
