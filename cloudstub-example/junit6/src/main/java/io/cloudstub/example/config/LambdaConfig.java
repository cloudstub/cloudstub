package io.cloudstub.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Configuration
class LambdaConfig extends AwsClientSupport {

    @Bean
    LambdaClient lambdaClient(@Value("${aws.endpoint-url:}") String endpointUrl) {
        return build(LambdaClient.builder(), endpointUrl);
    }
}
