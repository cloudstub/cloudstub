package io.cloudstub.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
class SnsConfig extends AwsClientSupport {

    @Bean
    SnsClient snsClient(@Value("${aws.endpoint-url:}") String endpointUrl) {
        return build(SnsClient.builder(), endpointUrl);
    }
}
