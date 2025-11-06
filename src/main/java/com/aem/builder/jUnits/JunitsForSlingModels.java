package com.aem.builder.jUnits;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.util.JavaFormatterUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.apache.tomcat.util.IntrospectionUtils.capitalize;

@Slf4j
public class JunitsForSlingModels {

    /**
     * Generates a basic JUnit test class for a Sling Model, including dynamic field tests.
     *
     * @param modelBasePath base output folder path (e.g., src/main/java/com/example/models)
     * @param packageName   package name of the model
     * @param className     name of the generated Sling Model class
     * @param fields        list of fields defined in the Sling model
     */
    public static void generateJUnitTestForModel(
            String modelBasePath, String packageName, String className, List<ComponentField> fields) throws IOException {

        // Correct test base path (already includes package structure)
        String testBasePath = modelBasePath.replace("src/main/java", "src/test/java");
        File testDir = new File(testBasePath);

        if (!testDir.exists()) {
            testDir.mkdirs();
        }

        String testClassName = className + "Test";
        File testFile = new File(testDir, testClassName + ".java");
        String testPackage = packageName;

        StringBuilder fieldTests = new StringBuilder();

        for (ComponentField field : fields) {
            String name = field.getFieldName();
            String type = field.getFieldType() != null ? field.getFieldType().toLowerCase() : "textfield";
            String getterName = "get" + capitalize(name);

            switch (type) {
                case "checkbox":
                case "switch":
                case "radiogroup":
                    fieldTests.append("    @Test\n")
                            .append("    void test").append(capitalize(name)).append("() {\n")
                            .append("        assertTrue(model.").append(getterName).append("(), ")
                            .append("\"").append(name).append(" should be true\");\n")
                            .append("    }\n\n");
                    break;

                case "numberfield":
                    fieldTests.append("    @Test\n")
                            .append("    void test").append(capitalize(name)).append("() {\n")
                            .append("        assertEquals(123, model.").append(getterName).append("(), ")
                            .append("\"").append(name).append(" should match dummy value\");\n")
                            .append("    }\n\n");
                    break;

                case "multifield":
                    fieldTests.append("    @Test\n")
                            .append("    void test").append(capitalize(name)).append("() {\n")
                            .append("        assertNotNull(model.").append(getterName).append("());\n")
                            .append("        assertFalse(model.").append(getterName).append("().isEmpty(), ")
                            .append("\"").append(name).append(" list should not be empty\");\n")
                            .append("    }\n\n");
                    break;

                default:
                    fieldTests.append("    @Test\n")
                            .append("    void test").append(capitalize(name)).append("() {\n")
                            .append("        assertNotNull(model.").append(getterName).append("(), ")
                            .append("\"").append(name).append(" should not be null\");\n")
                            .append("    }\n\n");
                    break;
            }
        }


        // --- If file already exists, update it instead of skipping ---
        if (testFile.exists()) {
            log.info("🔄 Updating existing JUnit for {}", className);
            String existingContent = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

            // --- Normalize line endings ---
            existingContent = existingContent.replace("\r\n", "\n");

            // --- Always keep testModelNotNull intact ---
            String modelNotNullBlock = """
                        @Test
                        void testModelNotNull() {
                            assertNotNull(model, "Model should be adaptable from resource");
                        }
                    """;

            if (existingContent.contains("testModelNotNull")) {
                existingContent = existingContent.replaceAll(
                        "(?s)@Test\\s+void\\s+testModelNotNull\\(\\)\\s*\\{.*?\\}",
                        modelNotNullBlock.trim());
            }

            // --- Remove old auto-generated test methods ---
            existingContent = existingContent.replaceAll(
                    "(?s)@Test\\s+void\\s+(?!testModelNotNull)test[A-Z][A-Za-z0-9_]*\\(\\)\\s*\\{.*?\\}"
                    ,
                    "");

            // --- Insert new test methods before final closing brace ---
            int insertPos = existingContent.lastIndexOf('}');
            if (insertPos > 0) {
                StringBuilder newFieldTests = new StringBuilder("\n");
                for (ComponentField field : fields) {
                    String fieldName = field.getFieldName();
                    String getterName = "get" + capitalize(fieldName);
                    newFieldTests.append("    @Test\n")
                            .append("    void test").append(capitalize(fieldName)).append("() {\n")
                            .append("        assertNotNull(model.").append(getterName).append("(), ")
                            .append("\"").append(fieldName).append(" should not be null\");\n")
                            .append("    }\n\n");
                }

                existingContent = new StringBuilder(existingContent)
                        .insert(insertPos - 1, newFieldTests.toString())
                        .toString();
            }

            FileUtils.writeStringToFile(testFile, existingContent, StandardCharsets.UTF_8);
            JavaFormatterUtil.cleanAndFormatJavaFile(testFile);
            log.info("✅ Existing JUnit updated for model: {}", className);
            return;
        }


        // --- Build resource properties dynamically ---
        StringBuilder resourceProps = new StringBuilder();
        resourceProps.append("\"sling:resourceType\", \"")
                .append(packageName).append("/components/")
                .append(className.replace("Model", "").toLowerCase())
                .append("\"");
        for (ComponentField field : fields) {
            String name = field.getFieldName();
            String type = field.getFieldType() != null ? field.getFieldType().toLowerCase() : "textfield";

            switch (type) {
                case "numberfield":
                    resourceProps.append(", \"").append(name).append("\", 123");
                    break;
                case "checkbox":
                case "switch":
                case "radiogroup":
                    resourceProps.append(", \"").append(name).append("\", true");
                    break;
                case "multifield":
                    resourceProps.append(", \"").append(name).append("\", new String[]{\"Item1\", \"Item2\"}");
                    break;
                case "select":
                case "multiselect":
                case "pathfield":
                case "tagfield":
                case "colorfield":
                    resourceProps.append(", \"").append(name).append("\", \"#FF5733\"");
                    break;
                case "datepicker":
                    resourceProps.append(", \"").append(name).append("\", \"2025-01-01\"");
                    break;
                case "hidden":
                case "fileupload":
                case "password":
                case "richtext":
                case "textarea":
                default:
                    resourceProps.append(", \"").append(name).append("\", \"Test")
                            .append(capitalize(name)).append("\"");
                    break;
            }
        }


// --- Generate JUnit file content ---
        String testContent =
                "package " + testPackage + ";\n\n" +
                        "import io.wcm.testing.mock.aem.junit5.AemContext;\n" +
                        "import io.wcm.testing.mock.aem.junit5.AemContextExtension;\n" +
                        "import org.junit.jupiter.api.BeforeEach;\n" +
                        "import org.junit.jupiter.api.Test;\n" +
                        "import org.junit.jupiter.api.extension.ExtendWith;\n" +
                        "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                        "@ExtendWith(AemContextExtension.class)\n" +
                        "public class " + testClassName + " {\n\n" +
                        "    private final AemContext context = new AemContext();\n" +
                        "    private " + className + " model;\n\n" +
                        "    @BeforeEach\n" +
                        "    void setUp() {\n" +
                        "        context.create().resource(\"/content/test\",\n" +
                        "            new Object[]{" + resourceProps + "});\n" +
                        "        model = context.currentResource(\"/content/test\").adaptTo(" + className + ".class);\n" +
                        "        assertNotNull(model, \"Model should be adaptable from resource\");\n" +
                        "    }\n\n" +
                        fieldTests +
                        "}\n";

        FileUtils.writeStringToFile(testFile, testContent, StandardCharsets.UTF_8);
        JavaFormatterUtil.cleanAndFormatJavaFile(testFile);
        log.info("✅ New JUnit Test generated for model: {}", className);
    }


    /**
     * Deletes the JUnit test class for a given Sling Model or Multifield model.
     *
     * @param projectName the project name (used for resolving base path)
     * @param basePackage the model's package (e.g. com.aem.project.core.models)
     * @param className   the model class name (e.g. HeroModel or Child)
     */
    public static void deleteJUnitForModel(String projectName, String basePackage, String className) {
        try {
            Path testClassPath = Paths.get("generated-projects", projectName,
                    "core/src/test/java", basePackage.replace(".", "/"),
                    className + "Test.java");

            if (Files.exists(testClassPath)) {
                Files.delete(testClassPath);
                log.info("Deleted JUnit test for model: {}", testClassPath);
            } else {
                log.info("No JUnit test found for model '{}'", className);
            }
        } catch (Exception e) {
            log.warn("Failed to delete JUnit test for model '{}': {}", className, e.getMessage());
        }
    }


}
