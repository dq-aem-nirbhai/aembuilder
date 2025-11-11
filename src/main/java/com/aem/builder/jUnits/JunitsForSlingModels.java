package com.aem.builder.jUnits;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.tomcat.util.IntrospectionUtils.capitalize;

@Slf4j
public class JunitsForSlingModels {

    public static void generateJUnitTestForModel(String modelBasePath, String testBasePath, String packageName, String className) {
        try {
            Path modelFilePath = Paths.get(modelBasePath, className + ".java");
            if (!Files.exists(modelFilePath)) {
                log.warn("Model file not found: {}", modelFilePath);
                return;
            }

            // Read model content
            String modelContent = Files.readString(modelFilePath);
            Map<String, String> fieldTypes = extractFieldTypes(modelContent);

            if (fieldTypes.isEmpty()) {
                log.warn("No fields found in {}", className);
                return;
            }

            // Create test folder
            Path testFolderPath = Paths.get(testBasePath);
            Files.createDirectories(testFolderPath);

            File testFile = testFolderPath.resolve(className + "Test.java").toFile();

            // ------------------ Full-model test ------------------
            StringBuilder modelTest = new StringBuilder();
            modelTest.append("    @Test\n")
                    .append("    void test").append(className).append("Model() {\n")
                    .append("        assertNotNull(model);\n");

            for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
                String field = entry.getKey();
                String type = entry.getValue();
                String getter;
                if ("boolean".equalsIgnoreCase(type)) {
                    getter = "is" + capitalize(field); // boolean fields use is<Field>()
                } else {
                    getter = "get" + capitalize(field);
                }
                if (type.startsWith("List<") || type.startsWith("java.util.List<")) {
                    modelTest.append("        assertNotNull(model.").append(getter).append("(), \"")
                            .append(field).append(" list should not be null\");\n");
                    modelTest.append("        assertFalse(model.").append(getter).append("().isEmpty(), \"")
                            .append(field).append(" list should not be empty\");\n");
                    modelTest.append("        assertNotNull(model.").append(getter)
                            .append("().get(0), \"First element of ").append(field).append(" list should not be null\");\n");
                } else {
                    modelTest.append("        assertEquals(")
                            .append(getMockValue(type, field, false))
                            .append(", model.").append(getter).append("(), ")
                            .append("\"").append(field).append(" should match expected value\");\n");
                }


            }
            modelTest.append("    }\n\n");

            // ------------------ Individual getter tests ------------------
            StringBuilder individualTests = new StringBuilder();
            for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
                String field = entry.getKey();
                String type = entry.getValue();
                String getter;
                if ("boolean".equalsIgnoreCase(type)) {
                    getter = "is" + capitalize(field); // boolean fields use is<Field>()
                } else {
                    getter = "get" + capitalize(field);
                }

                individualTests.append("    @Test\n")
                        .append("    void test").append(capitalize(getter)).append("() {\n")
                        .append("        assertNotNull(model.").append(getter).append("(), ")
                        .append("\"").append(field).append(" should not be null\");\n")
                        .append("    }\n\n");
            }


            // ------------------ Setup block ------------------
            String modelName = className.replace("Model", "").toLowerCase();

            String setupBlock =
                    "    @BeforeEach\n" +
                            "    void setUp() {\n" +
                            "        context.addModelsForClasses(" + className + ".class);\n" +
                            "        context.load().json(\"/" + modelName + "/" + className + "Test.json\", \"/content\");\n" +
                            "        context.currentResource(\"/content\");\n" +
                            "        model = context.currentResource().adaptTo(" + className + ".class);\n" +
                            "    }\n\n";


            // ------------------ Build test class content ------------------
            String testContent =
                    "package " + packageName + ";\n\n" +
                            "import io.wcm.testing.mock.aem.junit5.AemContext;\n" +
                            "import io.wcm.testing.mock.aem.junit5.AemContextExtension;\n" +
                            "import org.junit.jupiter.api.BeforeEach;\n" +
                            "import org.junit.jupiter.api.Test;\n" +
                            "import org.junit.jupiter.api.extension.ExtendWith;\n" +
                            "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                            "@ExtendWith(AemContextExtension.class)\n" +
                            "class " + className + "Test {\n\n" +
                            "    private final AemContext context = new AemContext();\n" +
                            "    private " + className + " model;\n\n" +
                            setupBlock +
                            modelTest +
                            individualTests +
                            "}\n";

            // Write test file
            FileUtils.writeStringToFile(testFile, testContent, StandardCharsets.UTF_8);
            log.info("JUnit Test generated: {}", testFile.getAbsolutePath());

            // ------------------ Generate JSON test resource ------------------
            // Base test resources path
            String[] packageParts = packageName.split("\\.");
            String projectName = packageParts.length > 2 ? packageParts[2] : "default";

            Path resourcesBase = Paths.get("generated-projects", projectName, "core/src/test/resources");

            // Create folder for model (e.g., smilemodel)
            Path modelJsonFolder = resourcesBase.resolve(className.replace("Model", "").toLowerCase());
            Files.createDirectories(modelJsonFolder);

            // JSON file path
            File jsonFile = modelJsonFolder.resolve(className + "Test.json").toFile();

            // Build JSON content
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\n");
            jsonBuilder.append("  \"jcr:primaryType\": \"nt:unstructured\",\n");

            String resourceType = projectName.toLowerCase() + "/components/" + className.replace("Model", "").toLowerCase();
            jsonBuilder.append("  \"sling:resourceType\": \"").append(resourceType).append("\",\n");

            int count = 0;
            for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
                if (count++ > 0) jsonBuilder.append(",\n");

                String fieldName = entry.getKey();
                String type = entry.getValue();

                // --- Handle List types properly ---
                if (type.startsWith("List<") || type.startsWith("java.util.List<")) {
                    String childClass = type.substring(type.indexOf("<") + 1, type.indexOf(">")).trim();

                    // --- Case 1: List<String> — simple multi-valued property ---
                    if (childClass.equalsIgnoreCase("String")) {
                        jsonBuilder.append("  \"").append(fieldName).append("\": [\"Item1\", \"Item2\"]");
                    }
                    // --- Case 2: List<ChildModel> — nested node structure (multifield) ---
                    else {
                        jsonBuilder.append("  \"").append(fieldName).append("\": {\n");
                        jsonBuilder.append("    \"item0\": {\n");
                        jsonBuilder.append("      \"jcr:primaryType\": \"nt:unstructured\",\n");

                        // Try to read the child model to generate its fields dynamically
                        Path childModelPath = Paths.get(modelBasePath).resolve(childClass + ".java");
                        if (Files.exists(childModelPath)) {
                            String childContent = Files.readString(childModelPath);
                            Map<String, String> childFields = extractFieldTypes(childContent);
                            int subCount = 0;
                            for (Map.Entry<String, String> sub : childFields.entrySet()) {
                                if (subCount++ > 0) jsonBuilder.append(",\n");
                                jsonBuilder.append("      \"").append(sub.getKey()).append("\": ")
                                        .append(getMockValue(sub.getValue(), sub.getKey(), true));
                            }
                            jsonBuilder.append("\n");
                        } else {
                            jsonBuilder.append("      \"sampleField\": \"TestValue\"\n");
                        }

                        jsonBuilder.append("    },\n");
                        jsonBuilder.append("    \"item1\": {\n");
                        jsonBuilder.append("      \"jcr:primaryType\": \"nt:unstructured\",\n");
                        jsonBuilder.append("      \"sampleField\": \"TestValue\"\n");
                        jsonBuilder.append("    }\n");
                        jsonBuilder.append("  }");
                    }
                }
                // --- Regular field (not a list) ---
                else {
                    jsonBuilder.append("  \"").append(fieldName).append("\": ")
                            .append(getMockValue(type, fieldName, true));
                }
            }
            jsonBuilder.append("\n}");


// Write JSON to file
            FileUtils.writeStringToFile(jsonFile, jsonBuilder.toString(), StandardCharsets.UTF_8);
            log.info("JSON test data generated at: {}", jsonFile.getAbsolutePath());


        } catch (Exception e) {
            log.error("Error generating JUnit for model {}: {}", className, e.getMessage(), e);
        }
    }

    private static Map<String, String> extractFieldTypes(String modelContent) {
        Map<String, String> fieldMap = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("private\\s+([A-Za-z0-9_<>\\[\\]]+)\\s+([a-zA-Z0-9_]+)\\s*;");
        Matcher matcher = pattern.matcher(modelContent);
        while (matcher.find()) {
            fieldMap.put(matcher.group(2), matcher.group(1));
        }
        return fieldMap;
    }

    private static String getMockValue(String type, String fieldName, boolean forJson) {
        type = type.toLowerCase();
        fieldName = fieldName.toLowerCase();

        if (fieldName.contains("path") || fieldName.contains("url") || fieldName.contains("link")) {
            return forJson ? "\"/content/dam/sample-file.txt\"" : "\"/content/dam/sample-file.txt\"";
        }

        switch (type) {
            case "list<string>":
            case "multiselect":
            case "multifield":
            case "tagfield":
                return forJson ? "[\"Item1\", \"Item2\"]" : "java.util.Arrays.asList(\"Item1\", \"Item2\")";

            case "boolean":
            case "checkbox":
            case "switch":
                return forJson ? "true" : "true";

            case "int":
            case "integer":
            case "long":
            case "numberfield":
                return forJson ? "123" : "123";

            case "double":
                return forJson ? "12.34" : "12.34";

            default:
                return forJson ? "\"TestValue\"" : "\"TestValue\"";
        }
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

            // Delete JSON test resource
            Path resourcesBase = Paths.get("generated-projects", projectName, "core/src/test/resources");
            Path modelJsonFolder = resourcesBase.resolve(className.replace("Model", "").toLowerCase());
            if (Files.exists(modelJsonFolder)) {
                try {
                    FileUtils.deleteDirectory(modelJsonFolder.toFile());
                    log.info("Deleted JSON test data folder for model: {}", modelJsonFolder);
                } catch (IOException ex) {
                    log.warn("Failed to delete JSON test data for model '{}': {}", className, ex.getMessage());
                }
            }


        } catch (Exception e) {
            log.warn("Failed to delete JUnit test for model '{}': {}", className, e.getMessage());
        }
    }
}


