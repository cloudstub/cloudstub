package io.cloudstub.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.example.service.FunctionInvoker;
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class FunctionInvokerIntegrationTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

    @Autowired FunctionInvoker invoker;

    @Test
    void invokeDeploysTheFunctionAndReturnsItsResult() {
        assertEquals("{\"order\":42}", invoker.invoke("{\"order\":42}"));
    }
}
