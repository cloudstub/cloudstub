package io.cloudmock.codegen;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ModuleGeneratorTest {

    @Test
    void generatesCorrectFilesForJsonProtocol() throws Exception {
        URL fixture = getClass().getResource("/fixtures/test-service.smithy");
        assertNotNull(fixture, "test-service.smithy fixture not found on classpath");
        Path modelPath = Path.of(fixture.toURI());

        GenerationResult result = new ModuleGenerator().generate(modelPath, "0.1.0-SNAPSHOT");

        assertEquals("testservice", result.serviceId());
        assertEquals("cloudmock-testservice", result.moduleName());

        Map<String, String> files = result.files().stream()
                .collect(Collectors.toMap(GeneratedFile::relativePath, GeneratedFile::content));

        assertTrue(files.containsKey("build.gradle"), "build.gradle missing");
        String buildGradle = files.get("build.gradle");
        assertTrue(buildGradle.contains("io.cloudmock:cloudmock-core:0.1.0-SNAPSHOT"),
                "build.gradle must reference published artifact coordinates");
        assertFalse(buildGradle.contains("project(':cloudmock-core')"),
                "build.gradle must not reference project path");

        String spiKey = "src/main/resources/META-INF/services/io.cloudmock.core.spi.CloudMockService";
        assertTrue(files.containsKey(spiKey), "META-INF/services file missing");
        assertTrue(files.get(spiKey).contains("CloudMockTestServiceService"),
                "META-INF/services must name the generated class");

        String serviceKey = "src/main/java/io/cloudmock/testservice/CloudMockTestServiceService.java";
        assertTrue(files.containsKey(serviceKey), "service class missing");
        String serviceClass = files.get(serviceKey);
        assertTrue(serviceClass.contains("registerJsonTargetStub"),
                "service class must use JSON target protocol");
        assertTrue(serviceClass.contains("loadTemplate("),
                "service class must load templates from classpath");
        assertFalse(serviceClass.contains("private static final String CREATE_FOO"),
                "service class must not inline operation templates as constants");

        assertTrue(files.containsKey("src/main/resources/templates/CreateFoo.hbs"),
                "CreateFoo.hbs template missing");
        assertTrue(files.containsKey("src/main/resources/templates/DeleteFoo.hbs"),
                "DeleteFoo.hbs template missing");

        String createFooTemplate = files.get("src/main/resources/templates/CreateFoo.hbs");
        assertFalse(createFooTemplate.contains("\\\""),
                "template file must not contain Java-escaped quotes");
        assertTrue(createFooTemplate.contains("fooId"),
                "CreateFoo.hbs must reference the fooId output field");

        String testKey = "src/test/java/io/cloudmock/testservice/CloudMockTestServiceServiceTest.java";
        assertTrue(files.containsKey(testKey), "test class missing");
        String testClass = files.get(testKey);
        assertTrue(testClass.contains("void createFoo()"), "test class must stub createFoo");
        assertTrue(testClass.contains("void deleteFoo()"), "test class must stub deleteFoo");

        // Builder classes
        String createBuilderKey = "src/main/java/io/cloudmock/testservice/response/CreateFooResponseBuilder.java";
        String deleteBuilderKey = "src/main/java/io/cloudmock/testservice/response/DeleteFooResponseBuilder.java";
        assertTrue(files.containsKey(createBuilderKey), "CreateFooResponseBuilder missing");
        assertTrue(files.containsKey(deleteBuilderKey), "DeleteFooResponseBuilder missing");

        String createBuilder = files.get(createBuilderKey);
        assertTrue(createBuilder.contains("package io.cloudmock.testservice.response"),
                "builder must be in the response sub-package");
        assertTrue(createBuilder.contains("public final class CreateFooResponseBuilder"),
                "builder class declaration missing");
        // No required fields in CreateFooOutput → no-arg constructor
        assertTrue(createBuilder.contains("public CreateFooResponseBuilder() {}"),
                "no required fields → no-arg constructor expected");
        assertTrue(createBuilder.contains("public CreateFooResponseBuilder fooId(String value)"),
                "builder must have fluent setter for fooId");
        assertTrue(createBuilder.contains("public CreateFooResponseBuilder name(String value)"),
                "builder must have fluent setter for name");
        assertTrue(createBuilder.contains("return ResponseSupport.toJson(_fields)"),
                "JSON protocol builder must delegate to the shared ResponseSupport serialiser");
        assertFalse(createBuilder.contains("WireMock"), "builder must have no WireMock dependency");

        // Serialisation helpers live in one shared class per module, not copied into every builder.
        String supportKey = "src/main/java/io/cloudmock/testservice/response/ResponseSupport.java";
        assertTrue(files.containsKey(supportKey), "shared ResponseSupport class missing");
        assertTrue(files.get(supportKey).contains("static String toJson("),
                "ResponseSupport must expose the toJson helper");
        assertFalse(createBuilder.contains("static String toJson("),
                "builder must not inline the serialiser — it belongs in ResponseSupport");

        // DeleteFooOutput is empty → build() returns '{}'
        String deleteBuilder = files.get(deleteBuilderKey);
        assertTrue(deleteBuilder.contains("public DeleteFooResponseBuilder() {}"),
                "empty output shape → no-arg constructor");

        assertEquals(9, files.size(), "unexpected number of generated files");
    }

    @Test
    void generatesCorrectFilesFromJsonAstFormat() throws Exception {
        URL fixture = getClass().getResource("/fixtures/widget-service.json");
        assertNotNull(fixture, "widget-service.json fixture not found on classpath");
        Path modelPath = Path.of(fixture.toURI());

        GenerationResult result = new ModuleGenerator().generate(modelPath, "0.1.0-SNAPSHOT");

        assertEquals("widget", result.serviceId());
        assertEquals("cloudmock-widget", result.moduleName());

        Map<String, String> files = result.files().stream()
                .collect(Collectors.toMap(GeneratedFile::relativePath, GeneratedFile::content));

        String serviceKey = "src/main/java/io/cloudmock/widget/CloudMockWidgetService.java";
        assertTrue(files.containsKey(serviceKey), "service class missing");
        assertTrue(files.get(serviceKey).contains("registerJsonTargetStub"),
                "service class must use JSON target protocol");

        assertTrue(files.containsKey("src/main/resources/templates/CreateWidget.hbs"),
                "CreateWidget.hbs template missing");
        assertTrue(files.containsKey("src/main/resources/templates/DeleteWidget.hbs"),
                "DeleteWidget.hbs template missing");

        assertTrue(files.get("src/main/resources/templates/CreateWidget.hbs").contains("widgetId"),
                "CreateWidget.hbs must reference the widgetId output field");
        assertTrue(files.get("src/main/resources/templates/DeleteWidget.hbs").contains("{}"),
                "DeleteWidget.hbs must produce an empty JSON object for Unit output");

        assertTrue(files.containsKey("src/main/java/io/cloudmock/widget/response/CreateWidgetResponseBuilder.java"),
                "CreateWidgetResponseBuilder missing");
        assertTrue(files.containsKey("src/main/java/io/cloudmock/widget/response/DeleteWidgetResponseBuilder.java"),
                "DeleteWidgetResponseBuilder missing");
        assertTrue(files.containsKey("src/main/java/io/cloudmock/widget/response/ResponseSupport.java"),
                "shared ResponseSupport class missing");

        assertEquals(9, files.size(), "unexpected number of generated files");
    }

    /**
     * Regression for issue #0019 review (finding #3): real AWS models (e.g. S3) reference traits not
     * bundled with smithy-aws-traits (smithy.rules#*, smithy.waiters#*). The generator must tolerate
     * unknown traits and skip strict validation rather than aborting model assembly.
     */
    @Test
    void generatesFromModelWithUnknownTraits() throws Exception {
        URL fixture = getClass().getResource("/fixtures/unknown-trait-service.smithy");
        assertNotNull(fixture, "unknown-trait-service.smithy fixture not found on classpath");
        Path modelPath = Path.of(fixture.toURI());

        GenerationResult result = new ModuleGenerator().generate(modelPath, "0.1.0-SNAPSHOT");

        assertEquals("gadget", result.serviceId());

        Map<String, String> files = result.files().stream()
                .collect(Collectors.toMap(GeneratedFile::relativePath, GeneratedFile::content));

        String serviceClass = files.get("src/main/java/io/cloudmock/gadget/CloudMockGadgetService.java");
        assertNotNull(serviceClass, "service class missing");
        assertTrue(serviceClass.contains("registerRestStub"),
                "restXml service must route via REST path stubs");
        assertTrue(files.containsKey("src/main/resources/templates/GetGadget.hbs"),
                "GetGadget.hbs template missing");
    }

    @Test
    void formUrlBuilderProducesXmlEnvelopeWithResponseMetadata() throws Exception {
        // SNS-style FORM_URL services use <OpResponse><OpResult>...</OpResult><ResponseMetadata>...</ResponseMetadata></OpResponse>
        URL fixture = getClass().getResource("/fixtures/test-form-url-service.smithy");
        assertNotNull(fixture, "test-form-url-service.smithy fixture not found on classpath");
        Path modelPath = Path.of(fixture.toURI());

        GenerationResult result = new ModuleGenerator().generate(modelPath, "0.1.0-SNAPSHOT");

        Map<String, String> files = result.files().stream()
                .collect(Collectors.toMap(GeneratedFile::relativePath, GeneratedFile::content));

        String builderKey = "src/main/java/io/cloudmock/formtest/response/PublishMessageResponseBuilder.java";
        assertTrue(files.containsKey(builderKey), "PublishMessageResponseBuilder missing");

        String builder = files.get(builderKey);
        assertTrue(builder.contains("\"<PublishMessageResponse>\""), "FORM_URL builder must open with <OpResponse>");
        assertTrue(builder.contains("ResponseSupport.toXml(\"PublishMessageResult\""), "FORM_URL builder must wrap fields in <OpResult>");
        assertTrue(builder.contains("ResponseMetadata"), "FORM_URL builder must include ResponseMetadata");
        assertTrue(builder.contains("UUID.randomUUID()"), "FORM_URL builder must generate a fresh RequestId per call");
        assertTrue(builder.contains("\"</PublishMessageResponse>\""), "FORM_URL builder must close the envelope");
    }

    @Test
    void builderSanitizesReservedWordMemberNames() throws Exception {
        Map<String, String> files = generate("/fixtures/tricky-service.smithy");
        String builder = files.get("src/main/java/io/cloudmock/tricky/response/DescribeResponseBuilder.java");
        assertNotNull(builder, "DescribeResponseBuilder missing");

        // Required reserved word 'default' → constructor param 'default_', but the wire key stays 'default'.
        assertTrue(builder.contains("public DescribeResponseBuilder(String default_)"),
                "reserved-word required field must become a sanitised constructor parameter");
        assertTrue(builder.contains("_fields.put(\"default\", default_)"),
                "wire key must remain the original member name even when the identifier is sanitised");

        // Optional reserved word 'class' → setter 'class_', and @jsonName drives the wire key.
        assertTrue(builder.contains("public DescribeResponseBuilder class_(String value)"),
                "reserved-word optional field must become a sanitised setter");
    }

    @Test
    void builderUsesWireNameOverridesForSerialisation() throws Exception {
        // JSON: @jsonName("Class") overrides the wire key, while the Java setter keeps the member name.
        Map<String, String> json = generate("/fixtures/tricky-service.smithy");
        String jsonBuilder = json.get("src/main/java/io/cloudmock/tricky/response/DescribeResponseBuilder.java");
        assertTrue(jsonBuilder.contains("_fields.put(\"Class\", value)"),
                "@jsonName must drive the JSON wire key");
        assertFalse(jsonBuilder.contains("_fields.put(\"class\", value)"),
                "member name must not be used as the wire key when @jsonName is present");

        // XML: @xmlName("Identifier") overrides the element name, setter keeps the member name.
        Map<String, String> xml = generate("/fixtures/xml-name-service.smithy");
        String xmlBuilder = xml.get("src/main/java/io/cloudmock/xmlname/response/FetchResponseBuilder.java");
        assertNotNull(xmlBuilder, "FetchResponseBuilder missing");
        assertTrue(xmlBuilder.contains("public FetchResponseBuilder thingId(String value)"),
                "Java setter must use the member name");
        assertTrue(xmlBuilder.contains("_fields.put(\"Identifier\", value)"),
                "@xmlName must drive the XML element name");
    }

    @Test
    void builderMapsScalarShapesToPreciseJavaTypes() throws Exception {
        Map<String, String> files = generate("/fixtures/tricky-service.smithy");
        String builder = files.get("src/main/java/io/cloudmock/tricky/response/DescribeResponseBuilder.java");

        assertTrue(builder.contains("createdAt(java.time.Instant value)"), "timestamp → java.time.Instant");
        assertTrue(builder.contains("amount(java.math.BigDecimal value)"), "bigDecimal → java.math.BigDecimal");
        assertTrue(builder.contains("count(Long value)"), "long → Long");
        assertTrue(builder.contains("status(String value)"), "enum → String");
        assertFalse(builder.contains("createdAt(java.util.Map"),
                "timestamp must not fall through to the Map default");
    }

    private Map<String, String> generate(String fixture) throws Exception {
        URL url = getClass().getResource(fixture);
        assertNotNull(url, fixture + " not found on classpath");
        GenerationResult result = new ModuleGenerator().generate(Path.of(url.toURI()), "0.1.0-SNAPSHOT");
        return result.files().stream()
                .collect(Collectors.toMap(GeneratedFile::relativePath, GeneratedFile::content));
    }

    @Test
    void builderEnforcesRequiredFieldsViaConstructor() throws Exception {
        URL fixture = getClass().getResource("/fixtures/required-field-service.smithy");
        assertNotNull(fixture, "required-field-service.smithy fixture not found on classpath");
        Path modelPath = Path.of(fixture.toURI());

        GenerationResult result = new ModuleGenerator().generate(modelPath, "0.1.0-SNAPSHOT");

        Map<String, String> files = result.files().stream()
                .collect(Collectors.toMap(GeneratedFile::relativePath, GeneratedFile::content));

        String builderKey = "src/main/java/io/cloudmock/requiredfield/response/CreateItemResponseBuilder.java";
        assertTrue(files.containsKey(builderKey), "CreateItemResponseBuilder missing");

        String builder = files.get(builderKey);
        // Required fields itemId and arn must appear as constructor parameters, not fluent setters
        assertTrue(builder.contains("public CreateItemResponseBuilder(String itemId, String arn)"),
                "required fields must become constructor parameters");
        assertTrue(builder.contains("_fields.put(\"itemId\", itemId)"),
                "constructor must initialise required field itemId");
        assertTrue(builder.contains("_fields.put(\"arn\", arn)"),
                "constructor must initialise required field arn");
        // Optional field description must be a fluent setter, not in constructor
        assertTrue(builder.contains("public CreateItemResponseBuilder description(String value)"),
                "optional field must be a fluent setter");
        assertFalse(builder.contains("public CreateItemResponseBuilder() {}"),
                "builder with required fields must not have no-arg constructor");
    }

    /**
     * Regression for issue #0019 review (finding #3): with validation disabled, a service that
     * resolves to zero operations (wrong file / structurally broken model) must fail loudly rather
     * than silently emit a useless empty module.
     */
    @Test
    void failsLoudlyWhenServiceHasNoOperations() throws Exception {
        URL fixture = getClass().getResource("/fixtures/empty-service.smithy");
        assertNotNull(fixture, "empty-service.smithy fixture not found on classpath");
        Path modelPath = Path.of(fixture.toURI());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ModuleGenerator().generate(modelPath, "0.1.0-SNAPSHOT"));
        assertTrue(ex.getMessage().contains("0 operations"),
                "error must explain the service resolved no operations, got: " + ex.getMessage());
    }

    @Test
    void generatesXmlTemplatesAndRestRoutingForRestXmlProtocol() throws Exception {
        URL fixture = getClass().getResource("/fixtures/photo-service.smithy");
        assertNotNull(fixture, "photo-service.smithy fixture not found on classpath");
        Path modelPath = Path.of(fixture.toURI());

        GenerationResult result = new ModuleGenerator().generate(modelPath, "0.1.0-SNAPSHOT");

        assertEquals("photo", result.serviceId());

        Map<String, String> files = result.files().stream()
                .collect(Collectors.toMap(GeneratedFile::relativePath, GeneratedFile::content));

        String serviceClass = files.get("src/main/java/io/cloudmock/photo/CloudMockPhotoService.java");
        assertNotNull(serviceClass, "service class missing");
        assertTrue(serviceClass.contains("registerRestStub"),
                "restXml service must route via REST path stubs");
        // Greedy label {key+} must span path segments; normal label {key} must not.
        assertTrue(serviceClass.contains("/photos/.+"),
                "GetPhoto {key+} greedy label must compile to .+ so object keys with slashes match");
        assertTrue(serviceClass.contains("/photos/[^/]+"),
                "PutPhoto {key} normal label must compile to [^/]+");

        String getPhoto = files.get("src/main/resources/templates/GetPhoto.hbs");
        assertNotNull(getPhoto, "GetPhoto.hbs template missing");
        assertTrue(getPhoto.contains("<GetPhotoOutput>") && getPhoto.contains("<photoId>"),
                "restXml template must produce an XML body");
        assertFalse(getPhoto.contains("\"photoId\""),
                "restXml template must not produce a JSON body");

        // restXml builder — should produce XML, not JSON
        String getPhotoBuilderKey = "src/main/java/io/cloudmock/photo/response/GetPhotoResponseBuilder.java";
        assertTrue(files.containsKey(getPhotoBuilderKey), "GetPhotoResponseBuilder missing");
        String getPhotoBuilder = files.get(getPhotoBuilderKey);
        assertTrue(getPhotoBuilder.contains("ResponseSupport.toXml"), "restXml builder must use XML serialiser");
        assertFalse(getPhotoBuilder.contains("toJson"), "restXml builder must not use JSON serialiser");
        assertTrue(getPhotoBuilder.contains("GetPhotoOutput"), "restXml builder must use output shape name as XML root");

        // restXml module's shared support must expose toXml, not toJson
        String photoSupport = files.get("src/main/java/io/cloudmock/photo/response/ResponseSupport.java");
        assertNotNull(photoSupport, "shared ResponseSupport class missing");
        assertTrue(photoSupport.contains("static String toXml("), "ResponseSupport must expose toXml for restXml");
        assertFalse(photoSupport.contains("static String toJson("), "restXml ResponseSupport must not expose toJson");

        assertTrue(files.containsKey("src/main/java/io/cloudmock/photo/response/PutPhotoResponseBuilder.java"),
                "PutPhotoResponseBuilder missing");
    }

    /**
     * The generated response builders are self-contained (JDK types only — no cloudmock-core or AWS SDK
     * imports), so they can be compiled in isolation. String-content assertions cannot catch a syntactic
     * defect in generated Java; this test actually invokes the system Java compiler over every generated
     * builder, across all protocols, and fails if any does not compile.
     */
    @Test
    void generatedResponseBuildersCompile() throws Exception {
        String[] fixtures = {
                "/fixtures/test-service.smithy",          // JSON_TARGET
                "/fixtures/photo-service.smithy",         // REST_XML (incl. empty output shape)
                "/fixtures/test-form-url-service.smithy",  // FORM_URL
                "/fixtures/required-field-service.smithy", // required → constructor params
                "/fixtures/widget-service.json",          // JSON AST input
                "/fixtures/tricky-service.smithy",        // reserved-word identifiers + precise types
                "/fixtures/xml-name-service.smithy",      // @xmlName wire override
        };
        for (String fixture : fixtures) {
            URL url = getClass().getResource(fixture);
            assertNotNull(url, fixture + " not found on classpath");
            GenerationResult result = new ModuleGenerator().generate(Path.of(url.toURI()), "0.1.0-SNAPSHOT");
            assertResponseBuildersCompile(result);
        }
    }

    private static void assertResponseBuildersCompile(GenerationResult result) throws Exception {
        Path srcRoot = Files.createTempDirectory("cm-codegen-compile");
        List<File> sources = new ArrayList<>();
        for (GeneratedFile f : result.files()) {
            if (f.relativePath().contains("/response/") && f.relativePath().endsWith(".java")) {
                Path dest = srcRoot.resolve(f.relativePath());
                Files.createDirectories(dest.getParent());
                Files.writeString(dest, f.content());
                sources.add(dest.toFile());
            }
        }
        assertFalse(sources.isEmpty(), "no response builder sources generated for " + result.moduleName());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "system Java compiler unavailable — run tests on a JDK, not a JRE");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Path classesOut = Files.createDirectories(srcRoot.resolve("classes"));
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesOut.toFile()));
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(sources);
            boolean ok = compiler.getTask(null, fm, diagnostics, null, null, units).call();

            String errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
            assertTrue(ok, "generated response builders for " + result.moduleName()
                    + " failed to compile:\n" + errors);
        }
    }
}
