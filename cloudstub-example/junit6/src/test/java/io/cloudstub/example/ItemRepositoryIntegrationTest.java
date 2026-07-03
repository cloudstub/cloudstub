package io.cloudstub.example;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.example.service.ItemRepository;
import io.cloudstub.junit.CloudStubExtension;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class ItemRepositoryIntegrationTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

    @Autowired ItemRepository repository;

    @Test
    void savedItemIsReadBack() {
        repository.save("order-1", "placed");
        assertEquals(Optional.of("placed"), repository.find("order-1"));
    }

    @Test
    void absentItemReturnsEmpty() {
        assertTrue(repository.find("never-written").isEmpty());
    }
}
