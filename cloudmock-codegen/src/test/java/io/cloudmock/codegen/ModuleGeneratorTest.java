package io.cloudmock.codegen;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
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

        assertEquals(6, files.size(), "unexpected number of generated files");
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

        assertEquals(6, files.size(), "unexpected number of generated files");

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
    }
}
