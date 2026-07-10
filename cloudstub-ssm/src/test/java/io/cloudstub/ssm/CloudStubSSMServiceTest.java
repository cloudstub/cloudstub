package io.cloudstub.ssm;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterMetadata;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.Tag;

class CloudStubSSMServiceTest {

    // HttpClient is not AutoCloseable on the Java 17 baseline, so it is held as a shared field.
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static CloudStub cloudMock;
    static SsmClient ssm;

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubSSMService());
        cloudMock.start();
        ssm =
                SsmClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
    }

    @AfterAll
    static void stop() {
        ssm.close();
        cloudMock.stop();
    }

    /** A unique parameter name so accumulated state cannot leak between tests. */
    private String newName() {
        return "/test/" + UUID.randomUUID();
    }

    /**
     * Sends a raw JSON request with X-Amz-Target — verifies stub registration and JSON matching.
     */
    @Test
    void rawJsonRequestMatchesStub() throws Exception {
        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                .header("Content-Type", "application/x-amz-json-1.1")
                                .header("X-Amz-Target", "AmazonSSM.DescribeParameters")
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(
                200, response.statusCode(), "JSON stub did not match — check stub registration");
    }

    @Test
    void putThenGetReturnsTheStoredValue() {
        String name = newName();
        long version = ssm.putParameter(b -> b.name(name).value("s3cr3t")).version();
        assertEquals(1L, version, "a fresh parameter is version 1");

        Parameter parameter = ssm.getParameter(b -> b.name(name)).parameter();
        assertEquals(name, parameter.name());
        assertEquals("s3cr3t", parameter.value());
        assertEquals(ParameterType.STRING, parameter.type());
        assertEquals(1L, parameter.version());
        assertTrue(parameter.arn().endsWith(":parameter" + name));
    }

    @Test
    void overwriteRequiredToReplaceAndBumpsVersion() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("v1"));

        assertThrows(
                Exception.class,
                () -> ssm.putParameter(b -> b.name(name).value("v2")),
                "a second put without Overwrite must be rejected");

        long version = ssm.putParameter(b -> b.name(name).value("v2").overwrite(true)).version();
        assertEquals(2L, version, "an overwrite advances the version");
        assertEquals("v2", ssm.getParameter(b -> b.name(name)).parameter().value());
    }

    @Test
    void overwriteWithoutTypePreservesTheExistingType() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("v1").type(ParameterType.SECURE_STRING));
        ssm.putParameter(b -> b.name(name).value("v2").overwrite(true));

        assertEquals(
                ParameterType.SECURE_STRING,
                ssm.getParameter(b -> b.name(name)).parameter().type(),
                "a type-less overwrite must not downgrade the parameter type");
    }

    @Test
    void getMissingParameterThrowsParameterNotFound() {
        assertThrows(
                ParameterNotFoundException.class, () -> ssm.getParameter(b -> b.name(newName())));
    }

    @Test
    void getParametersSeparatesFoundFromInvalid() {
        String present = newName();
        String absent = newName();
        ssm.putParameter(b -> b.name(present).value("here"));

        GetParametersResponse response =
                ssm.getParameters(b -> b.names(present, absent).withDecryption(true));
        assertEquals(1, response.parameters().size());
        assertEquals(present, response.parameters().get(0).name());
        assertEquals(List.of(absent), response.invalidParameters());
    }

    @Test
    void getParametersByPathReturnsHierarchyMembers() {
        String base = "/path/" + UUID.randomUUID();
        ssm.putParameter(b -> b.name(base + "/db/host").value("h"));
        ssm.putParameter(b -> b.name(base + "/db/port").value("5432"));
        ssm.putParameter(b -> b.name(base + "/api/key").value("k"));

        GetParametersByPathResponse recursive =
                ssm.getParametersByPath(b -> b.path(base).recursive(true));
        assertEquals(3, recursive.parameters().size(), "recursive returns all descendants");

        GetParametersByPathResponse direct =
                ssm.getParametersByPath(b -> b.path(base + "/db").recursive(false));
        assertEquals(2, direct.parameters().size(), "non-recursive returns direct children only");
    }

    @Test
    void deleteRemovesTheParameter() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("temp"));
        ssm.deleteParameter(b -> b.name(name));
        assertThrows(ParameterNotFoundException.class, () -> ssm.getParameter(b -> b.name(name)));
    }

    @Test
    void deleteParametersReportsDeletedAndInvalid() {
        String present = newName();
        String absent = newName();
        ssm.putParameter(b -> b.name(present).value("x"));

        var response = ssm.deleteParameters(b -> b.names(present, absent));
        assertEquals(List.of(present), response.deletedParameters());
        assertEquals(List.of(absent), response.invalidParameters());
    }

    @Test
    void describeParametersIncludesAStoredParameter() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("d").type(ParameterType.STRING_LIST));
        List<ParameterMetadata> metadata = ssm.describeParameters().parameters();
        assertTrue(
                metadata.stream().anyMatch(m -> m.name().equals(name)),
                "described parameters must include the stored one");
    }

    @Test
    void getParameterHistoryReturnsTheCurrentVersion() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("h1"));
        ssm.putParameter(b -> b.name(name).value("h2").overwrite(true));

        var history = ssm.getParameterHistory(b -> b.name(name)).parameters();
        assertEquals(1, history.size(), "only the current version is retained");
        assertEquals(2L, history.get(0).version());
    }

    @Test
    void tagsRoundTripThroughListTags() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("v"));
        ssm.addTagsToResource(
                b ->
                        b.resourceType("Parameter")
                                .resourceId(name)
                                .tags(
                                        Tag.builder().key("env").value("test").build(),
                                        Tag.builder().key("team").value("core").build()));

        Map<String, String> tags =
                ssm
                        .listTagsForResource(b -> b.resourceType("Parameter").resourceId(name))
                        .tagList()
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value));
        assertEquals(Map.of("env", "test", "team", "core"), tags);

        ssm.removeTagsFromResource(
                b -> b.resourceType("Parameter").resourceId(name).tagKeys("env"));
        Map<String, String> after =
                ssm
                        .listTagsForResource(b -> b.resourceType("Parameter").resourceId(name))
                        .tagList()
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value));
        assertEquals(Map.of("team", "core"), after);
    }

    @Test
    void taggingAMissingParameterIsRejected() {
        assertThrows(
                Exception.class,
                () ->
                        ssm.addTagsToResource(
                                b ->
                                        b.resourceType("Parameter")
                                                .resourceId(newName())
                                                .tags(Tag.builder().key("k").value("v").build())),
                "tagging a parameter that does not exist must be rejected");
    }

    @Test
    void labelParameterVersionReturnsTheVersion() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("v"));
        var response = ssm.labelParameterVersion(b -> b.name(name).labels("prod"));
        assertEquals(1L, response.parameterVersion());
        assertTrue(response.invalidLabels().isEmpty());
    }

    @Test
    void labelingAMissingVersionIsRejected() {
        String name = newName();
        ssm.putParameter(b -> b.name(name).value("v"));
        assertThrows(
                Exception.class,
                () ->
                        ssm.labelParameterVersion(
                                b -> b.name(name).parameterVersion(99L).labels("prod")),
                "labeling a version that does not exist must be rejected");
    }
}
