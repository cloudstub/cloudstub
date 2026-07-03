package io.cloudstub.example.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;

/** Invokes a Lambda function with a JSON payload, deploying it on first use. */
@Service
public class FunctionInvoker {

    private final LambdaClient lambda;
    private final String functionName = "processor";
    private boolean deployed;

    public FunctionInvoker(LambdaClient lambda) {
        this.lambda = lambda;
    }

    /**
     * Deploys the function if this is the first call, then invokes it and returns the response
     * payload.
     */
    public String invoke(String jsonPayload) {
        ensureDeployed();
        return lambda.invoke(
                        b ->
                                b.functionName(functionName)
                                        .payload(SdkBytes.fromUtf8String(jsonPayload)))
                .payload()
                .asUtf8String();
    }

    private void ensureDeployed() {
        if (deployed) {
            return;
        }
        lambda.createFunction(
                b ->
                        b.functionName(functionName)
                                .runtime("nodejs20.x")
                                .role("arn:aws:iam::000000000000:role/lambda-role")
                                .handler("index.handler")
                                .code(
                                        c ->
                                                c.zipFile(
                                                        SdkBytes.fromUtf8String(
                                                                "exports.handler=async e=>e;"))));
        deployed = true;
    }
}
