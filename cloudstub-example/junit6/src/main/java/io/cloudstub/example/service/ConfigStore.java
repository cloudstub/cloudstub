package io.cloudstub.example.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

/** Stores and retrieves application configuration in the SSM Parameter Store. */
@Service
public class ConfigStore {

    private final SsmClient ssm;

    public ConfigStore(SsmClient ssm) {
        this.ssm = ssm;
    }

    /** Stores {@code value} under {@code name}, overwriting any existing value. */
    public void put(String name, String value) {
        ssm.putParameter(b -> b.name(name).value(value).overwrite(true));
    }

    /** Returns the value stored under {@code name}, or empty if the parameter does not exist. */
    public Optional<String> get(String name) {
        try {
            return Optional.of(ssm.getParameter(b -> b.name(name)).parameter().value());
        } catch (ParameterNotFoundException e) {
            return Optional.empty();
        }
    }

    /** Returns the value stored under {@code name}, or {@code fallback} if it is absent. */
    public String getOrDefault(String name, String fallback) {
        return get(name).orElse(fallback);
    }
}
