# Fault Injection

CloudMock can simulate three categories of AWS failure at the test-method level. Faults are applied by annotating individual test methods and are automatically cleared after each test, even if the test throws.

## Annotations

### `@SimulateThrottle`

Makes all requests to the specified service return HTTP 400 with error code `ThrottlingException`. The AWS SDK translates this to a service-specific exception (e.g. `SqsException` for SQS).

```java
@SimulateThrottle(service = "sqs")
@Test
void retryLogicHandlesThrottling() {
    SqsException ex = assertThrows(SqsException.class, () ->
            sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
    assertEquals("ThrottlingException", ex.awsErrorDetails().errorCode());
}
```

### `@SimulateTimeout`

Injects a 30-second fixed delay on the server side. The AWS SDK's call timeout fires before the response arrives, producing `ApiCallTimeoutException`. Configure a short timeout on the client so the test finishes quickly.

```java
@SimulateTimeout(service = "sqs")
@Test
void timeoutHandlerRaisesCallTimeoutException() {
    try (SqsClient shortTimeout = SqsClient.builder()
            .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofMillis(500)))
            .build()) {

        assertThrows(ApiCallTimeoutException.class, () ->
                shortTimeout.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
    }
}
```

### `@SimulateNetworkBrownout`

Causes a configurable fraction of requests to fail with a connection reset (`SdkClientException`). The remainder are served normally.

```java
@SimulateNetworkBrownout(service = "sqs", rate = 1.0) // (1)!
@Test
void allRequestsFailWithConnectionReset() {
    assertThrows(SdkClientException.class, () ->
            sqsClient.sendMessage(b -> b.queueUrl(QUEUE_URL).messageBody("test")));
}
```

1. Use `rate = 0.0` or `rate = 1.0` for deterministic assertions. Fractional rates (e.g. `0.5`) are statistical and unsuitable for exact-count assertions.

## Cleanup contract

`CloudMockExtension` clears all faults after **every** test method, including tests that throw. This means:

- A fault applied in test N is guaranteed to be gone for test N+1.
- Tests do not need to call `clearFaults()` manually.
- The cleanup happens in `afterTestExecution`, before `@AfterEach` methods.

```java
@SimulateThrottle(service = "sqs")
@Test
void throttled() {
    assertThrows(SqsException.class, () -> sqsClient.sendMessage(...));
}

@Test
void normalAfterThrottle() {
    // Throttle was cleared — this succeeds
    assertDoesNotThrow(() -> sqsClient.sendMessage(...));
}
```

## Multiple annotations

All three annotations are repeatable. Multiple annotations on the same method are applied simultaneously.

```java
@SimulateThrottle(service = "sqs")
@SimulateThrottle(service = "secretsmanager")
@Test
void bothServicesThrottled() {
    assertThrows(SqsException.class, () -> sqsClient.sendMessage(...));
    assertThrows(SecretsManagerException.class, () -> smClient.getSecretValue(...));
}
```

## Programmatic fault injection

When using `CloudMock` directly (without `CloudMockExtension`), faults are managed manually.

```java
cloudMock.simulateThrottle("sqs");
// ... run test assertions ...
cloudMock.clearFaults("sqs");   // or clearAllFaults()
```

The programmatic API is also available when `CloudMockExtension` is used via `@RegisterExtension`, but the annotation-driven approach is preferred for test-method-level granularity.
