package io.cloudstub.lambda;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;

class CloudStubLambdaServiceTest {

    // HttpClient is not AutoCloseable on the Java 17 baseline, so it is held as a shared field.
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static CloudStub cloudMock;
    static LambdaClient lambda;

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubLambdaService());
        cloudMock.start();

        lambda =
                LambdaClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
    }

    @AfterAll
    static void stop() {
        lambda.close();
        cloudMock.stop();
    }

    /** Creates a uniquely named function and returns its name. */
    private String newFunction() {
        String name = "fn-" + UUID.randomUUID();
        lambda.createFunction(
                b ->
                        b.functionName(name)
                                .runtime("nodejs20.x")
                                .role("arn:aws:iam::000000000000:role/lambda-role")
                                .handler("index.handler")
                                .code(c -> c.zipFile(SdkBytes.fromUtf8String("dummy-zip"))));
        return name;
    }

    @Test
    void rawRestPathMatchesStub() throws Exception {
        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                "http://localhost:"
                                                        + cloudMock.port()
                                                        + "/2015-03-31/functions"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "ListFunctions REST path did not match");
    }

    @Test
    void createFunctionReturnsConfiguration() {
        String name = "orders-" + UUID.randomUUID();
        var response =
                lambda.createFunction(
                        b ->
                                b.functionName(name)
                                        .runtime("nodejs20.x")
                                        .role("arn:aws:iam::000000000000:role/lambda-role")
                                        .handler("index.handler")
                                        .code(c -> c.zipFile(SdkBytes.fromUtf8String("zip")))
                                        .description("order processor")
                                        .timeout(15)
                                        .memorySize(256));
        assertEquals(name, response.functionName());
        assertEquals("nodejs20.x", response.runtimeAsString());
        assertEquals(15, response.timeout());
        assertEquals(256, response.memorySize());
        assertTrue(response.functionArn().endsWith(":function:" + name));
    }

    @Test
    void createdFunctionIsReturnedByGetFunction() {
        String name = newFunction();
        GetFunctionResponse response = lambda.getFunction(b -> b.functionName(name));
        assertEquals(name, response.configuration().functionName());
        assertEquals("index.handler", response.configuration().handler());
        assertNotNull(response.code());
    }

    @Test
    void getFunctionConfigurationReturnsFlatConfig() {
        String name = newFunction();
        var config = lambda.getFunctionConfiguration(b -> b.functionName(name));
        assertEquals(name, config.functionName());
        assertEquals("nodejs20.x", config.runtimeAsString());
    }

    @Test
    void recreatingAFunctionThrowsConflict() {
        String name = newFunction();
        assertThrows(
                ResourceConflictException.class,
                () ->
                        lambda.createFunction(
                                b ->
                                        b.functionName(name)
                                                .runtime("nodejs20.x")
                                                .role("arn:aws:iam::0:role/r")
                                                .handler("index.handler")
                                                .code(
                                                        c ->
                                                                c.zipFile(
                                                                        SdkBytes.fromUtf8String(
                                                                                "z")))));
    }

    @Test
    void listFunctionsIncludesCreatedFunctions() {
        String name = newFunction();
        assertTrue(
                lambda.listFunctions().functions().stream()
                        .anyMatch(f -> name.equals(f.functionName())));
    }

    @Test
    void updateFunctionConfigurationMergesFields() {
        String name = newFunction();
        lambda.updateFunctionConfiguration(
                b -> b.functionName(name).timeout(30).description("updated"));

        var config = lambda.getFunctionConfiguration(b -> b.functionName(name));
        assertEquals(30, config.timeout());
        assertEquals("updated", config.description());
        assertEquals(
                "nodejs20.x", config.runtimeAsString(), "merge must preserve untouched fields");
    }

    @Test
    void updateFunctionCodeChangesCodeSha256() {
        String name = newFunction();
        String before = lambda.getFunctionConfiguration(b -> b.functionName(name)).codeSha256();
        lambda.updateFunctionCode(
                b -> b.functionName(name).zipFile(SdkBytes.fromUtf8String("brand-new-code")));
        String after = lambda.getFunctionConfiguration(b -> b.functionName(name)).codeSha256();
        assertNotEquals(before, after, "new code must produce a new CodeSha256");
    }

    @Test
    void invokeEchoesThePayload() {
        String name = newFunction();
        InvokeResponse response =
                lambda.invoke(
                        b ->
                                b.functionName(name)
                                        .payload(SdkBytes.fromUtf8String("{\"hello\":\"world\"}")));
        assertEquals(200, response.statusCode());
        assertEquals("{\"hello\":\"world\"}", response.payload().asUtf8String());
        assertEquals("$LATEST", response.executedVersion());
    }

    @Test
    void deleteFunctionRemovesIt() {
        String name = newFunction();
        lambda.deleteFunction(b -> b.functionName(name));
        assertThrows(
                ResourceNotFoundException.class,
                () -> lambda.getFunction(b -> b.functionName(name)));
    }

    @Test
    void invokeOnMissingFunctionThrowsResourceNotFound() {
        assertThrows(
                ResourceNotFoundException.class,
                () ->
                        lambda.invoke(
                                b ->
                                        b.functionName("no-such-fn")
                                                .payload(SdkBytes.fromUtf8String("{}"))));
    }

    @Test
    void tagsRoundTripThroughListTags() {
        String name = newFunction();
        String arn = "arn:aws:lambda:us-east-1:000000000000:function:" + name;
        lambda.tagResource(b -> b.resource(arn).tags(Map.of("team", "payments")));

        assertEquals("payments", lambda.listTags(b -> b.resource(arn)).tags().get("team"));
    }
}
