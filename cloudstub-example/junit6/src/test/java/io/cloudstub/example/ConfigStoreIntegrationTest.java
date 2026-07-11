package io.cloudstub.example;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.example.service.ConfigStore;
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class ConfigStoreIntegrationTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

    @Autowired ConfigStore configStore;

    @Test
    void putThenGetReturnsStoredValue() {
        configStore.put("/app/db-url", "jdbc:postgresql://db/app");
        assertEquals("jdbc:postgresql://db/app", configStore.get("/app/db-url").orElseThrow());
    }

    @Test
    void getReturnsEmptyForUnknownParameter() {
        assertTrue(configStore.get("/app/missing").isEmpty());
    }

    @Test
    void getOrDefaultFallsBackWhenAbsent() {
        assertEquals("off", configStore.getOrDefault("/app/feature", "off"));
    }
}
