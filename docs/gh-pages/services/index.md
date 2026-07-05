# Services

CloudStub mocks one AWS service per module. Type to filter, or pick a service below.

<input type="text" id="service-filter" class="md-input" placeholder="Filter services…" aria-label="Filter services" autocomplete="off" style="width:100%;padding:0.55rem 0.8rem;margin:0.6rem 0 1.2rem;border:1px solid var(--md-default-fg-color--lighter);border-radius:0.2rem;background:var(--md-default-bg-color);color:var(--md-default-fg-color);font-size:0.8rem;">

<div class="grid cards" markdown>

- **AWS Lambda**

    ---

    Stateful functions over the REST JSON protocol: a function created is returned by a later get, and Invoke echoes the payload.

    [Open the Lambda guide →](lambda.md)

- **AWS Secrets Manager**

    ---

    Stateful secret storage — a secret created is returned by a later get over the AWS JSON protocol.

    [Open the Secrets Manager guide →](secretsmanager.md)

- **Amazon DynamoDB**

    ---

    Stateful tables and items: an item put is returned by a later get, query, and scan.

    [Open the DynamoDB guide →](dynamodb.md)

- **Amazon S3**

    ---

    Stateful object storage — an object put is returned by a later get and listed in the bucket.

    [Open the S3 guide →](s3.md)

- **Amazon SNS**

    ---

    Stateful pub/sub topics and subscriptions over the AWS Query protocol.

    [Open the SNS guide →](sns.md)

- **Amazon SQS**

    ---

    Stateful message queues — a message sent is returned by a later receive.

    [Open the SQS guide →](sqs.md)

</div>
