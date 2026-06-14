# Roadmap

CloudStub targets the **most-used AWS services** rather than the full ~340-service AWS catalog. Any
service whose AWS SDK v2 API is one of the four supported wire protocols can be added; the protocol
determines which `StubRegistrar` method implements it.

## Support model

| Protocol                    | `StubRegistrar` method   | Match rule              | Reference module |
| --------------------------- | ------------------------ | ----------------------- | ---------------- |
| `awsJson1.0` / `awsJson1.1` | `registerJsonTargetStub` | `X-Amz-Target` header   | `cloudstub-sqs`  |
| `awsQuery` (XML / Form)     | `registerXmlFormStub`    | `Action` form parameter | `cloudstub-sns`  |
| `restXml`                   | `registerRestStub`       | HTTP method + path      | `cloudstub-s3`   |
| `restJson1`                 | `registerRestStub`       | HTTP method + path      | `cloudstub-s3`   |

Each protocol has a template overload (static, stateless) and a `StubHandler` overload (runs module code
per request with access to the shared `StateStore` for live data). New modules are generated from AWS Smithy
models via `cloudstub-codegen`.

Status legend: ✅ implemented · 🟡 scaffolded · ⬜ not started.

In the tables below, the **GH-Issue** for a not-started or scaffolded service tracks its implementation;
for an already-implemented service it tracks regenerating the hand-written module from its Smithy model
(only `cloudstub-s3` is Smithy-generated today).

## Target services

### JSON / X-Amz-Target — `registerJsonTargetStub`

| Service               | Status | GH-Issue                                                  |
| --------------------- | ------ | --------------------------------------------------------- |
| SQS                   | ✅     | [#137](https://github.com/cloudstub/cloudstub/issues/137) |
| Secrets Manager       | ✅     | [#139](https://github.com/cloudstub/cloudstub/issues/139) |
| DynamoDB              | 🟡     | [#115](https://github.com/cloudstub/cloudstub/issues/115) |
| Lambda (control)      | 🟡     | [#116](https://github.com/cloudstub/cloudstub/issues/116) |
| KMS                   | ⬜     | [#117](https://github.com/cloudstub/cloudstub/issues/117) |
| SSM (Parameter Store) | ⬜     | [#118](https://github.com/cloudstub/cloudstub/issues/118) |
| Kinesis               | ⬜     | [#119](https://github.com/cloudstub/cloudstub/issues/119) |
| Kinesis Firehose      | ⬜     | [#120](https://github.com/cloudstub/cloudstub/issues/120) |
| DynamoDB Streams      | ⬜     | [#121](https://github.com/cloudstub/cloudstub/issues/121) |
| CloudWatch Logs       | ⬜     | [#122](https://github.com/cloudstub/cloudstub/issues/122) |
| Step Functions        | ⬜     | [#123](https://github.com/cloudstub/cloudstub/issues/123) |
| EventBridge           | ⬜     | [#124](https://github.com/cloudstub/cloudstub/issues/124) |

### AWS Query / XML-Form — `registerXmlFormStub`

| Service              | Status | GH-Issue                                                  |
| -------------------- | ------ | --------------------------------------------------------- |
| SNS                  | ✅     | [#138](https://github.com/cloudstub/cloudstub/issues/138) |
| STS                  | ⬜     | [#125](https://github.com/cloudstub/cloudstub/issues/125) |
| IAM                  | ⬜     | [#126](https://github.com/cloudstub/cloudstub/issues/126) |
| CloudFormation       | ⬜     | [#127](https://github.com/cloudstub/cloudstub/issues/127) |
| CloudWatch (metrics) | ⬜     | [#128](https://github.com/cloudstub/cloudstub/issues/128) |
| EC2                  | ⬜     | [#129](https://github.com/cloudstub/cloudstub/issues/129) |
| SES                  | ⬜     | [#130](https://github.com/cloudstub/cloudstub/issues/130) |
| Redshift             | ⬜     | [#131](https://github.com/cloudstub/cloudstub/issues/131) |

### REST (restXml) — `registerRestStub`

| Service  | Status | GH-Issue                                                  |
| -------- | ------ | --------------------------------------------------------- |
| S3       | ✅     | —                                                         |
| Route 53 | ⬜     | [#132](https://github.com/cloudstub/cloudstub/issues/132) |

### REST (restJson1) — `registerRestStub`

| Service                    | Status | GH-Issue                                                  |
| -------------------------- | ------ | --------------------------------------------------------- |
| Lambda (invoke)            | ⬜     | [#116](https://github.com/cloudstub/cloudstub/issues/116) |
| API Gateway                | ⬜     | [#133](https://github.com/cloudstub/cloudstub/issues/133) |
| API Gateway v2             | ⬜     | [#134](https://github.com/cloudstub/cloudstub/issues/134) |
| ACM                        | ⬜     | [#135](https://github.com/cloudstub/cloudstub/issues/135) |
| OpenSearch / Elasticsearch | ⬜     | [#136](https://github.com/cloudstub/cloudstub/issues/136) |

## Build order

1. **Finish scaffolded:** DynamoDB, Lambda
2. **High-demand JSON (SQS pattern):** KMS, SSM, Kinesis, CloudWatch Logs, Step Functions, EventBridge
3. **Query family (SNS pattern):** STS, IAM, CloudFormation, CloudWatch, SES, EC2, Redshift
4. **REST family (S3 pattern):** Route 53, API Gateway (+ v2), ACM, OpenSearch, Lambda invoke

## Behavior simulation scope

These are deliberate effort-and-priority choices. They are deferred for later — as CloudStub matures, the
items below are candidates for implementation. Until a behavior is implemented, tests that depend on it
should use real AWS.

**Deferred, not yet implemented:**

- SQS FIFO deduplication / ordering
- S3 multipart upload lifecycle
- DynamoDB conditional expressions / transactions
- IAM policy evaluation

**Lower priority — hard or expensive to simulate faithfully:**

- Streaming / event-stream operations (Kinesis `SubscribeToShard`, Transcribe streaming, Lex audio)
- Real cryptographic semantics (KMS actual encrypt/decrypt)
- Binary or bidirectional protocols
