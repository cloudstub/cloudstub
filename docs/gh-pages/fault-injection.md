# Fault Injection

CloudStub can simulate three categories of AWS failure at the test-method level. Faults are applied by annotating individual test methods and are automatically cleared after each test, even if the test throws.

The examples below drive your own code (an `OrderService` that publishes to SQS, a `SecretLoader` that reads from Secrets Manager) so you assert how your code behaves when AWS misbehaves, not how the mock responds.

## Annotations

### `@SimulateThrottle`

Makes all requests to the specified service return HTTP 400 with error code `ThrottlingException`. The AWS SDK translates this to a service-specific exception (e.g. `SqsException` for SQS).

```java
@SimulateThrottle(service = "sqs")
@Test
void placeOrderFailsWhenSqsIsThrottled() {
    OrderService orders = new OrderService(sqs, queueUrl);

    SqsException ex = assertThrows(SqsException.class, () -> orders.placeOrder("sku-42"));
    assertEquals("ThrottlingException", ex.awsErrorDetails().errorCode());
}
```

### `@SimulateTimeout`

Injects a 30-second fixed delay on the server side. The AWS SDK's call timeout fires before the response arrives, producing `ApiCallTimeoutException`. Configure a short timeout on the client so the test finishes quickly.

```java
@SimulateTimeout(service = "sqs")
@Test
void placeOrderRaisesCallTimeout() {
    try (SqsClient shortTimeout = SqsClient.builder()
            .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofMillis(500)))
            .build()) {

        OrderService orders = new OrderService(shortTimeout, queueUrl);
        assertThrows(ApiCallTimeoutException.class, () -> orders.placeOrder("sku-42"));
    }
}
```

### `@SimulateNetworkBrownout`

Causes a configurable fraction of requests to fail with a connection reset (`SdkClientException`). The remainder are served normally.

```java
@SimulateNetworkBrownout(service = "sqs", rate = 1.0) // (1)!
@Test
void placeOrderFailsWhenEveryRequestResets() {
    OrderService orders = new OrderService(sqs, queueUrl);

    assertThrows(SdkClientException.class, () -> orders.placeOrder("sku-42"));
}
```

1. Use `rate = 0.0` or `rate = 1.0` for deterministic assertions. Fractional rates (e.g. `0.5`) are statistical and unsuitable for exact-count assertions.

## Cleanup contract

`CloudStubExtension` clears all faults after **every** test method, including tests that throw. This means:

- A fault applied in test N is guaranteed to be gone for test N+1.
- Tests do not need to call `clearFaults()` manually.
- The cleanup happens in `afterTestExecution`, before `@AfterEach` methods.

```java
@SimulateThrottle(service = "sqs")
@Test
void throttledOrderFails() {
    assertThrows(SqsException.class, () -> orders.placeOrder("sku-42"));
}

@Test
void orderSucceedsAfterThrottleCleared() {
    // The throttle from the previous test is gone, so this order goes through.
    assertDoesNotThrow(() -> orders.placeOrder("sku-42"));
}
```

## Multiple annotations

All three annotations are repeatable. Multiple annotations on the same method are applied simultaneously.

```java
@SimulateThrottle(service = "sqs")
@SimulateThrottle(service = "secretsmanager")
@Test
void orderAndSecretBothFailWhenThrottled() {
    assertThrows(SqsException.class, () -> orders.placeOrder("sku-42"));
    assertThrows(SecretsManagerException.class, () -> secrets.load("api-key"));
}
```

## Programmatic fault injection

When using `CloudStub` directly (without `CloudStubExtension`), faults are managed manually.

```java
cloudMock.simulateThrottle("sqs");
// ... run test assertions ...
cloudMock.clearFaults("sqs");   // or clearAllFaults()
```

The programmatic API is also available when `CloudStubExtension` is used via `@RegisterExtension`, but the annotation-driven approach is preferred for test-method-level granularity.
