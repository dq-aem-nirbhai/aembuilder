package com.aem.builder.jUnits;

import com.aem.builder.util.JavaFormatterUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.tomcat.util.IntrospectionUtils.capitalize;

@Slf4j
public class JunitsForSlingModels {

    public static void generateJUnitTestForModel(
            String projectName, String modelBasePath, String testBasePath, String packageName, String className) {

        try {
            Path modelFilePath = resolveModelPath(modelBasePath, packageName, className);

            if (!Files.exists(modelFilePath)) {
                log.warn("Model file not found: {}", modelFilePath);
                return;
            }

            String modelContent = Files.readString(modelFilePath);
            Map<String, String> fieldTypes = extractFieldTypes(modelContent);
            if (fieldTypes.isEmpty()) {
                log.info("No fields found in {}", className);
                return;
            }

            Path testFolderPath = resolveTestFolderPath(testBasePath, packageName);
            Files.createDirectories(testFolderPath);
            File testFile = testFolderPath.resolve(className + "Test.java").toFile();

            Path resourcesBase = Paths.get("generated-projects", projectName, "core/src/test/resources");
            Path modelJsonFolder = resourcesBase.resolve(className.replace("Model", "").toLowerCase());
            Files.createDirectories(modelJsonFolder);
            File jsonFile = modelJsonFolder.resolve(className + "Test.json").toFile();

            // -------------------- Handle JUnit --------------------
            if (testFile.exists()) {
                updateJUnitForModel(testFile, className, fieldTypes);
            } else {
                generateJUnitForModel(testFile, packageName, className, fieldTypes);
            }

            // -------------------- Handle JSON --------------------
            if (jsonFile.exists()) {
                updateJsonForModel(jsonFile, modelBasePath, projectName, className, fieldTypes);
            } else {
                generateJsonForModel(jsonFile, modelBasePath, projectName, className, fieldTypes);
            }

        } catch (Exception e) {
            log.error("Error generating JUnit/JSON for {}: {}", className, e.getMessage(), e);
        }
    }

    private static void generateJUnitForModel(File testFile, String packageName, String className, Map<String, String> fieldTypes)
            throws IOException {
        String content = buildJUnitTestContent(packageName, className, fieldTypes);
        FileUtils.writeStringToFile(testFile, content, StandardCharsets.UTF_8);
        JavaFormatterUtil.cleanAndFormatJavaFile(testFile);
        log.info("JUnit created: {}", testFile.getAbsolutePath());
    }

    private static void updateJUnitForModel(File testFile, String className, Map<String, String> fieldTypes)
            throws IOException {
        String existing = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);
        String updated = rebuildJUnitContent(existing, className, fieldTypes);
        FileUtils.writeStringToFile(testFile, updated, StandardCharsets.UTF_8);
        JavaFormatterUtil.cleanAndFormatJavaFile(testFile);
        log.info("JUnit updated: {}", testFile.getAbsolutePath());
    }

    private static void generateJsonForModel(File jsonFile, String modelBasePath, String projectName,
                                             String className, Map<String, String> fieldTypes) throws IOException {
        String content = buildJsonContent(modelBasePath, projectName, className, fieldTypes);
        FileUtils.writeStringToFile(jsonFile, content, StandardCharsets.UTF_8);
        log.info("JSON created: {}", jsonFile.getAbsolutePath());
    }

    // JSON UPDATION
    private static void updateJsonForModel(File jsonFile, String modelBasePath, String projectName,
                                           String className, Map<String, String> fieldTypes) throws IOException {
        String existing = FileUtils.readFileToString(jsonFile, StandardCharsets.UTF_8);
        String updated = refreshJsonContent(existing, modelBasePath, projectName, className, fieldTypes);
        FileUtils.writeStringToFile(jsonFile, updated, StandardCharsets.UTF_8);
        JavaFormatterUtil.cleanAndFormatJavaFile(jsonFile);
        log.info("JSON updated: {}", jsonFile.getAbsolutePath());
    }


    // SUPPORTING METHODS
    private static Path resolveModelPath(String modelBasePath, String packageName, String className) {
        Path path1 = Paths.get(modelBasePath, className + ".java");
        Path path2 = Paths.get(modelBasePath, packageName.replace(".", "/"), className + ".java");
        return Files.exists(path1) ? path1 : path2;
    }

    private static Path resolveTestFolderPath(String testBasePath, String packageName) {
        String packagePath = packageName.replace(".", "/");
        return testBasePath.contains(packagePath)
                ? Paths.get(testBasePath)
                : Paths.get(testBasePath, packagePath);
    }

    private static String buildJUnitTestContent(String packageName, String className, Map<String, String> fieldTypes) throws IOException {
        StringBuilder modelTest = new StringBuilder();
        modelTest.append("    @Test\n    void test").append(className).append("Model() {\n")
                .append("        assertNotNull(model);\n");

        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            String field = e.getKey();
            String type = e.getValue();
            String getter = ("boolean".equalsIgnoreCase(type)) ? "is" + capitalize(field) : "get" + capitalize(field);
            /*f (type.startsWith("List<")) {
                modelTest.append("        assertNotNull(model.").append(getter).append("(), \"")
                        .append(field).append(" list not null\");\n")
                        .append("        assertFalse(model.").append(getter).append("().isEmpty(), \"")
                        .append(field).append(" list not empty\");\n");
            } else {
                modelTest.append("        assertEquals(")
                        .append(getMockValue(type, field, false))
                        .append(", model.").append(getter).append("(), \"").append(field).append(" value match\");\n");
            }*/
            if (type.startsWith("List<") && !type.contains("String")) {
                // List of child objects
                String child = type.substring(type.indexOf("<") + 1, type.indexOf(">"));
                modelTest.append("        assertNotNull(model.").append(getter).append("(), \"")
                        .append(field).append(" list not null\");\n")
                        .append("        assertFalse(model.").append(getter).append("().isEmpty(), \"")
                        .append(field).append(" list not empty\");\n")
                        .append("        model.").append(getter).append("().forEach(child -> {\n")
                        .append("            assertNotNull(child);\n");

                // read child model file to generate assertions for its fields
                Path childModelPath = Paths.get("path/to/models", child + ".java");
                if (Files.exists(childModelPath)) {
                    String childContent = Files.readString(childModelPath);
                    Map<String, String> childFields = extractFieldTypes(childContent);
                    for (Map.Entry<String, String> cf : childFields.entrySet()) {
                        String childGetter = "get" + capitalize(cf.getKey());
                        modelTest.append("            assertNotNull(child.").append(childGetter)
                                .append("(), \"").append(cf.getKey()).append(" not null\");\n");
                    }
                }

                modelTest.append("        });\n");
            }

        }
        modelTest.append("    }\n\n");

        StringBuilder individualTests = new StringBuilder();
        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            String field = e.getKey();
            String type = e.getValue();
            String getter = ("boolean".equalsIgnoreCase(type)) ? "is" + capitalize(field) : "get" + capitalize(field);
            individualTests.append("    @Test\n")
                    .append("    void test").append(capitalize(getter)).append("() {\n")
                    .append("        assertNotNull(model.").append(getter).append("(), \"")
                    .append(field).append(" not null\");\n    }\n\n");
        }

        String setupBlock = "    @BeforeEach\n" +
                "    void setUp() {\n" +
                "        context.addModelsForClasses(" + className + ".class);\n" +
                "        context.load().json(\"/" + className.replace("Model", "").toLowerCase() + "/" +
                className + "Test.json\", \"/content\");\n" +
                "        context.currentResource(\"/content\");\n" +
                "        model = context.currentResource().adaptTo(" + className + ".class);\n    }\n\n";

        return "package " + packageName + ";\n\n" +
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
                setupBlock + modelTest + individualTests + "}\n";
    }

    private static String rebuildJUnitContent(String existing, String className, Map<String, String> fieldTypes) {
        // Remove all getter tests
        existing = existing.replaceAll("(?s)@Test\\s+void\\s+test(Get|Is)[A-Za-z0-9_]+\\(\\)\\s*\\{.*?\\n\\s*\\}", "");

        StringBuilder getters = new StringBuilder();
        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            String field = e.getKey();
            String type = e.getValue();
            String getter = ("boolean".equalsIgnoreCase(type)) ? "is" + capitalize(field) : "get" + capitalize(field);
            getters.append("    @Test\n")
                    .append("    void test").append(capitalize(getter)).append("() {\n")
                    .append("        assertNotNull(model.").append(getter).append("(), \"")
                    .append(field).append(" not null\");\n    }\n\n");
        }

        // Update the main model test
        StringBuilder modelTest = new StringBuilder();
        modelTest.append("    @Test\n    void test").append(className).append("Model() {\n")
                .append("        assertNotNull(model);\n");
        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            String field = e.getKey();
            String type = e.getValue();
            String getter = ("boolean".equalsIgnoreCase(type)) ? "is" + capitalize(field) : "get" + capitalize(field);
            modelTest.append("        assertNotNull(model.").append(getter).append("(), \"").append(field).append(" not null\");\n");
        }
        modelTest.append("    }\n\n");

        // Replace or append tests
        if (existing.contains("void test" + className + "Model()")) {
            existing = existing.replaceAll("(?s)@Test\\s+void\\s+test" + className + "Model\\(\\).*?\\}", modelTest.toString());
        } else {
            existing = existing.replaceAll("}\\s*$", modelTest + "}\n");
        }

        return existing.replaceAll("}\\s*$", getters + "}\n");
    }

    private static String buildJsonContent(String modelBasePath, String projectName, String className, Map<String, String> fieldTypes)
            throws IOException {

        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"jcr:primaryType\": \"nt:unstructured\",\n");
        json.append("  \"sling:resourceType\": \"")
                .append(projectName.toLowerCase()).append("/components/")
                .append(className.replace("Model", "").toLowerCase())
                .append("\",\n");

        int c = 0;

        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            if (c++ > 0) json.append(",\n");

            String field = e.getKey();
            String type = e.getValue().replace(" ", "");

            // LIST<STRING>
            if (type.equalsIgnoreCase("List<String>")) {
                json.append("  \"").append(field).append("\": ")
                        .append(getMockValue(type, field, true));
            }
            // MULTIFIELD LIST<CHILD>
            else if (type.startsWith("List<") && !type.contains("String")) {

                String child = type.substring(type.indexOf("<") + 1, type.indexOf(">"));

                int itemCount = 2;   // default (creation mode)

                json.append(buildMultifieldJson(field, child, modelBasePath, itemCount));
            }
            // SIMPLE FIELD
            else {
                json.append("  \"").append(field).append("\": ")
                        .append(getMockValue(type, field, true));
            }
        }

        json.append("\n}");
        return json.toString();
    }


    private static int getExistingItemCount(String json, String fieldName) {

        // Find full object block of the multifield
        Matcher m = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\\{([\\s\\S]*?)\\}").matcher(json);

        if (!m.find()) return 2;

        String block = m.group(1);

        Matcher itemMatcher = Pattern.compile("\"item(\\d+)\"").matcher(block);

        int max = -1;
        while (itemMatcher.find()) {
            int num = Integer.parseInt(itemMatcher.group(1));
            max = Math.max(max, num);
        }

        return (max == -1) ? 2 : max + 1;
    }


    private static String refreshJsonContent(String existingJsonString,
                                             String modelBasePath,
                                             String projectName,
                                             String className,
                                             Map<String, String> fieldTypes) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> existingJson = new LinkedHashMap<>();
        if (existingJsonString != null && !existingJsonString.isBlank()) {
            existingJson = mapper.readValue(existingJsonString, LinkedHashMap.class);
        }

        // FINAL JSON after sync
        Map<String, Object> finalJson = new LinkedHashMap<>();

        if (existingJson.containsKey("jcr:primaryType"))
            finalJson.put("jcr:primaryType", existingJson.get("jcr:primaryType"));
        else
            finalJson.put("jcr:primaryType", "nt:unstructured");

        finalJson.put("sling:resourceType",
                projectName.toLowerCase() + "/components/" +
                        className.replace("Model", "").toLowerCase()
        );

        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {

            String field = e.getKey();
            String type = e.getValue().replace(" ", "");

            boolean isExisting = existingJson.containsKey(field);

            // LIST<String> → tags/multiselect/etc
            if (type.equals("List<String>")) {

                if (isExisting) {
                    // A. Preserve
                    finalJson.put(field, existingJson.get(field));
                } else {
                    // Create new list
                    finalJson.put(field, Arrays.asList("Item1", "Item2"));
                }

                continue;
            }

            // MULTIFIELD → List<Child>
            if (type.startsWith("List<") && !type.contains("String")) {

                String child = type.substring(type.indexOf("<") + 1, type.indexOf(">"));

                Object oldVal = existingJson.get(field);

                if (oldVal instanceof Map) {
                    // A. Structure SAME → preserve entire block
                    finalJson.put(field, oldVal);
                } else {
                    // C. Structure changed → rebuild multifield JSON
                    int count = getExistingItemCount(existingJsonString, field);

                    String newJsonBlock = buildMultifieldJson(field, child, modelBasePath, count);

                    // convert string → map
                    Map<String, Object> newMap = mapper.readValue(
                            "{" + newJsonBlock + "}",
                            LinkedHashMap.class);

                    finalJson.put(field, newMap.get(field));
                }

                continue;
            }

            // SIMPLE FIELDS
            if (isExisting) {
                // A. preserve
                finalJson.put(field, existingJson.get(field));
            } else {
                // C. NEW FIELD → create mock based on field name/type
                finalJson.put(field, getMockValue(type, field, true));
            }
        }

        return mapper.writeValueAsString(finalJson);
    }


    private static Map<String, String> extractFieldTypes(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("private\\s+([\\w<>.,\\s]+)\\s+([a-zA-Z0-9_]+)\\s*;").matcher(content);
        while (m.find()) map.put(m.group(2), m.group(1));
        return map;
    }

private static String getMockValue(String type, String field, boolean forJson) {

    type = type.toLowerCase();
    field = field.toLowerCase();

    // ARRAY VALUES (must NOT add quotes)
    if (field.contains("tag")) {
        return forJson
                ? "[\"app:tags/tag1\", \"app:tags/tag2\"]"
                : "Arrays.asList(\"app:tags/tag1\", \"app:tags/tag2\")";
    }

    if (field.contains("select") || field.contains("radio")) {
        return forJson
                ? "[\"Option1\", \"Option2\"]"
                : "Arrays.asList(\"Option1\", \"Option2\")";
    }

    if (type.equals("list<string>")) {
        return forJson
                ? "[\"Item1\", \"Item2\"]"
                : "Arrays.asList(\"Item1\", \"Item2\")";
    }

    // SIMPLE STRING FIELDS
    String value;

    if (field.contains("url") || field.contains("link"))
        value = "/content/sample.html";

    else if (field.contains("image") || field.contains("file") || field.contains("asset"))
        value = "/content/dam/sample.jpg";

    else if (field.contains("path"))
        value = "/content/sample/path";

    else if (field.contains("color"))
        value = "#FF5733";

    else if (field.contains("date"))
        value = "2024-01-15";

    else {

        // TYPE-BASED LOGIC
        switch (type) {

            case "boolean":
                return "true";

            case "int":
            case "integer":
            case "long":
                return "123";

            case "double":
            case "float":
                return "12.34";

            case "password":
                value = "Password@123";
                break;

            case "string":
            case "charsequence":
                if (field.contains("text") || field.contains("description") || field.contains("rich")) {
                    value = "Sample content";
                } else {
                    value = "TestValue";
                }
                break;

            default:
                value = "TestValue";
        }
    }

    // JSON MODE → wrap string in quotes
    if (forJson) {
        // Don't quote numbers or booleans
        if (value.matches("true|false|\\d+(\\.\\d+)?")) {
            return value;
        }
        return "\"" + value + "\"";   // wrap in quotes
    }

    return "\"" + value + "\"";  // For Java test mock — keep inside quotes
}



    private static String buildMultifieldJson(String fieldName, String childClass,
                                              String modelBasePath, int itemCount) throws IOException {

        StringBuilder json = new StringBuilder("  \"" + fieldName + "\": {\n");

        Path childModelPath = Paths.get(modelBasePath, childClass + ".java");

        for (int i = 0; i < itemCount; i++) {
            json.append("    \"item").append(i).append("\": {\n");
            json.append("      \"jcr:primaryType\": \"nt:unstructured\",\n");

            if (Files.exists(childModelPath)) {
                String childContent = Files.readString(childModelPath);
                Map<String, String> childFields = extractFieldTypes(childContent);

                int c = 0;
                for (Map.Entry<String, String> e : childFields.entrySet()) {
                    if (c++ > 0) json.append(",\n");
                    json.append("      \"").append(e.getKey()).append("\": ")
                            .append(getMockValue(e.getValue(), e.getKey(), true));
                }
                json.append("\n");
            } else {
                json.append("      \"sampleField\": \"TestValue\"\n");
            }

            json.append("    }");
            if (i < itemCount - 1) json.append(",");
            json.append("\n");
        }

        json.append("  }");
        return json.toString();
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
                    log.info("Failed to delete JSON test data for model '{}': {}", className, ex.getMessage());
                }
            }


        } catch (Exception e) {
            log.info("Failed to delete JUnit test for model '{}': {}", className, e.getMessage());
        }
    }
}


