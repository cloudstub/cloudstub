package io.cloudstub.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.ssm.SsmClient;

@Configuration
class SsmConfig extends AwsClientSupport {

    @Bean
    SsmClient ssmClient(@Value("${aws.endpoint-url:}") String endpointUrl) {
        return build(SsmClient.builder(), endpointUrl);
    }
}
