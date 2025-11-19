package com.aem.builder.jUnits;

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

    /**
     * Generates or updates both JUnit test class and JSON test data file
     * for the given Sling Model class.
     *
     * <p>This is the main entry point invoked during component creation or update.
     * It resolves paths, extracts fields, and delegates to JUnit/JSON
     * generation or update methods.</p>
     *
     * @param projectName   Name of the project
     * @param modelBasePath Base path where Sling Models are located
     * @param testBasePath  Base path where test classes should be created
     * @param packageName   Package name of the Sling Model
     * @param className     Class name of the Sling Model
     */
    public static void generateJUnitTestForModel(
            String projectName, String modelBasePath, String testBasePath, String packageName, String className) {

        log.info("FILEGEN: Starting JUnit/JSON generation for model: {}", className);
        log.debug("FILEGEN: modelBasePath={}, testBasePath={}, package={}",
                modelBasePath, testBasePath, packageName);
        try {
            Path modelFilePath = resolveModelPath(modelBasePath, packageName, className);
            log.info("FILEGEN: Resolved model file path: {}", modelFilePath);
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

    /**
     * Creates a fresh JUnit test class for the Sling Model.
     *
     * @param testFile    Target JUnit file
     * @param packageName Package of the test class
     * @param className   Model class name
     * @param fieldTypes  Fields extracted from the model
     */
    private static void generateJUnitForModel(File testFile, String packageName, String className, Map<String, String> fieldTypes)
            throws IOException {

        log.info("FILEGEN: Creating new JUnit file: {}", testFile.getAbsolutePath());
        log.info("FILEGEN: Total fields detected for {} → {}", className, fieldTypes.size());

        String content = buildJUnitTestContent(packageName, className, fieldTypes);
        FileUtils.writeStringToFile(testFile, content, StandardCharsets.UTF_8);
        log.info("FILEGEN: JUnit created successfully for model: {}", className);
    }

    /**
     * Updates an existing JUnit test class by regenerating test methods
     * and refreshing the main test method.
     *
     * @param testFile   Existing JUnit file
     * @param className  Model class name
     * @param fieldTypes Fields found in the updated model
     */
    private static void updateJUnitForModel(File testFile, String className, Map<String, String> fieldTypes)
            throws IOException {

        log.info("FILEGEN: Updating existing JUnit for model: {}", className);
        log.info("FILEGEN: Reading existing file: {}", testFile.getAbsolutePath());

        String existing = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);
        String updated = rebuildJUnitContent(existing, className, fieldTypes);

        FileUtils.writeStringToFile(testFile, updated, StandardCharsets.UTF_8);
        log.info("FILEGEN: JUnit updated successfully for model: {}", className);
    }

    /**
     * Generates a new JSON test data file for the Sling Model.
     */
    private static void generateJsonForModel(File jsonFile, String modelBasePath, String projectName,
                                             String className, Map<String, String> fieldTypes) throws IOException {

        log.info("FILEGEN: Creating JSON test data for model: {}", className);
        log.info("FILEGEN: JSON path: {}", jsonFile.getAbsolutePath());

        String content = buildJsonContent(modelBasePath, projectName, className, fieldTypes);
        FileUtils.writeStringToFile(jsonFile, content, StandardCharsets.UTF_8);

        log.info("FILEGEN: JSON created successfully for model: {}", className);
    }

    /**
     * Updates an existing JSON test data file by syncing fields,
     * adding new ones, and preserving existing values.
     */
    private static void updateJsonForModel(File jsonFile, String modelBasePath, String projectName,
                                           String className, Map<String, String> fieldTypes) throws IOException {

        log.info("FILEGEN: Updating JSON for model: {}", className);
        log.info("FILEGEN: Reading existing JSON: {}", jsonFile.getAbsolutePath());

        String existing = FileUtils.readFileToString(jsonFile, StandardCharsets.UTF_8);
        String updated = refreshJsonContent(existing, modelBasePath, projectName, className, fieldTypes);

        FileUtils.writeStringToFile(jsonFile, updated, StandardCharsets.UTF_8);
        log.info("FILEGEN: JSON updated successfully for model: {}", className);
    }

    /**
     * Resolves the actual Java file path of a Sling Model based on base path + package.
     */
    private static Path resolveModelPath(String modelBasePath, String packageName, String className) {

        log.debug("FILEGEN: Resolving model path for {} in {}", className, modelBasePath);
        Path path1 = Paths.get(modelBasePath, className + ".java");
        Path path2 = Paths.get(modelBasePath, packageName.replace(".", "/"), className + ".java");

        Path chosen = Files.exists(path1) ? path1 : path2;

        log.debug("FILEGEN: Model path resolved to: {}", chosen);
        return chosen;
    }

    /**
     * Resolves the test folder path for the given package.
     * <p>
     * If the base test path already contains the package path, it is returned directly.
     * Otherwise, the package structure is appended to the base test folder.
     *
     * @param testBasePath base test folder path
     * @param packageName  Java package name
     * @return resolved test folder path
     */
    private static Path resolveTestFolderPath(String testBasePath, String packageName) {

        log.debug("FILEGEN: Resolving test folder path. base={}, package={}",
                testBasePath, packageName);

        String packagePath = packageName.replace(".", "/");
        Path resolved = testBasePath.contains(packagePath)
                ? Paths.get(testBasePath)
                : Paths.get(testBasePath, packagePath);

        log.debug("FILEGEN: Test folder resolved to {}", resolved);

        return resolved;
    }

    /**
     * Builds the complete JUnit test class content for a Sling Model.
     * <p>
     * Generates:
     * <ul>
     *     <li>Main model test method</li>
     *     <li>Individual getter test methods</li>
     *     <li>Setup block</li>
     *     <li>Imports and class structure</li>
     * </ul>
     *
     * @param packageName test class package
     * @param className   model class name
     * @param fieldTypes  map of model fields and their types
     * @return complete JUnit class content as a String
     */
    private static String buildJUnitTestContent(String packageName, String className, Map<String, String> fieldTypes) throws IOException {

        log.info("FILEGEN: Building new JUnit content for model: {}", className);
        log.debug("FILEGEN: Total fields detected = {}", fieldTypes.size());

        StringBuilder modelTest = new StringBuilder();
        modelTest.append("    @Test\n    void test").append(className).append("Model() {\n")
                .append("        assertNotNull(model);\n");

        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            String field = e.getKey();
            String type = e.getValue();
            String getter = ("boolean".equalsIgnoreCase(type)) ? "is" + capitalize(field) : "get" + capitalize(field);
            // Handle List<Child> multifield
            if (type.startsWith("List<") && !type.contains("String")) {

                String child = type.substring(type.indexOf("<") + 1, type.indexOf(">"));
                modelTest.append("        assertNotNull(model.").append(getter).append("(), \"")
                        .append(field).append(" list not null\");\n")
                        .append("        assertFalse(model.").append(getter).append("().isEmpty(), \"")
                        .append(field).append(" list not empty\");\n")
                        .append("        model.").append(getter).append("().forEach(child -> {\n")
                        .append("            assertNotNull(child);\n");

                // read child model fields and add assertions
                // Path childModelPath = Paths.get("path/to/models", child + ".java");
                // Correct path to child Sling Model file
                log.info("Childmodel class calling: ");
                Path childModelPath = Paths.get(
                        "core/src/main/java",
                        packageName.replace(".", "/"),
                        child + ".java"
                );
                if (Files.exists(childModelPath)) {
                    String childContent = Files.readString(childModelPath);
                    Map<String, String> childFields = extractFieldTypes(childContent);
                    for (Map.Entry<String, String> cf : childFields.entrySet()) {
                        String childGetter = "get" + capitalize(cf.getKey());
                        modelTest.append("            assertNotNull(child.")
                                .append(childGetter).append("(), \"")
                                .append(cf.getKey()).append(" not null\");\n");
                    }
                }

                modelTest.append("        });\n");
            }

            // ADD THIS ELSE BLOCK (THIS WAS MISSING)
            else {
                modelTest.append("        assertEquals(")
                        .append(getMockValue(type, field, false))
                        .append(", model.").append(getter).append("(), \"")
                        .append(field).append(" value match\");\n");
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
                "import static org.junit.jupiter.api.Assertions.*;\n" +
                "import java.util.Arrays;\n\n" +
                "@ExtendWith(AemContextExtension.class)\n" +
                "class " + className + "Test {\n\n" +
                "    private final AemContext context = new AemContext();\n" +
                "    private " + className + " model;\n\n" +
                setupBlock + modelTest + individualTests + "}\n";
    }

    /**
     * Rebuilds an existing JUnit test class by:
     * <ul>
     *     <li>Replacing the main test method</li>
     *     <li>Removing old getter test methods</li>
     *     <li>Regenerating updated getter test methods</li>
     * </ul>
     *
     * @param existing   the existing JUnit file content
     * @param className  model class name
     * @param fieldTypes updated field map of the Sling model
     * @return updated JUnit file content
     */
    private static String rebuildJUnitContent(String existing, String className, Map<String, String> fieldTypes) {

        log.info("FILEGEN: Rebuilding JUnit content for model: {}", className);
        log.debug("FILEGEN: Updating {} fields in existing test file", fieldTypes.size());

        // 1. Build new main model test method
        StringBuilder newMainMethod = new StringBuilder();
        newMainMethod.append("    @Test\n")
                .append("    void test").append(className).append("Model() {\n")
                .append("        assertNotNull(model);\n");

        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            String field = e.getKey();
            String type = e.getValue();
            String getter = ("boolean".equalsIgnoreCase(type))
                    ? "is" + capitalize(field)
                    : "get" + capitalize(field);

            newMainMethod.append("        assertNotNull(model.")
                    .append(getter).append("(), \"")
                    .append(field).append(" not null\");\n");
        }

        newMainMethod.append("    }\n\n");

        // 2. Replace the OLD main test method
        String methodRegex =
                "@Test\\s+void\\s+test" + className +
                        "Model\\(\\)[\\s\\S]*?(?=@Test|}\\s*$)";

        if (existing.matches("(?s).*" + methodRegex + ".*")) {
            existing = existing.replaceAll(
                    methodRegex,
                    Matcher.quoteReplacement("\n" + newMainMethod.toString()));
        } else {
            // no existing method → append it before last closing brace
            existing = existing.replaceAll("}\\s*$", Matcher.quoteReplacement(newMainMethod.toString()) + "}");
        }

        // 3. Remove ALL getter tests
        existing = existing.replaceAll(
                "(?s)@Test\\s+void\\s+test(?:Get|Is)[A-Za-z0-9_]+\\s*\\(\\)\\s*\\{[\\s\\S]*?\\}",
                ""
        );

        // 4. Generate fresh getter tests
        StringBuilder getterTests = new StringBuilder();
        for (Map.Entry<String, String> e : fieldTypes.entrySet()) {
            String field = e.getKey();
            String type = e.getValue();
            String getter = ("boolean".equalsIgnoreCase(type))
                    ? "is" + capitalize(field)
                    : "get" + capitalize(field);

            getterTests.append("    @Test\n")
                    .append("    void test").append(capitalize(getter)).append("() {\n")
                    .append("        assertNotNull(model.").append(getter)
                    .append("(), \"").append(field).append(" not null\");\n")
                    .append("    }\n\n");
        }

        // 5. Append all getter tests before last brace
        existing = existing.replaceAll("}\\s*$",
                Matcher.quoteReplacement(getterTests.toString()) + "}");

        return existing;
    }

    /**
     * Builds the initial JSON test data file for a Sling Model.
     * <p>
     * Supports:
     * <ul>
     *     <li>Simple fields</li>
     *     <li>List&lt;String&gt; fields</li>
     *     <li>Multifield List&lt;ChildModel&gt;</li>
     * </ul>
     *
     * @param modelBasePath base folder where models exist
     * @param projectName   project name for sling:resourceType
     * @param className     model class
     * @param fieldTypes    fields extracted from model
     * @return fully formatted JSON string
     */
    private static String buildJsonContent(String modelBasePath, String projectName, String className, Map<String, String> fieldTypes)
            throws IOException {

        log.info("FILEGEN: Building JSON test content for model: {}", className);
        log.debug("FILEGEN: Total fields for JSON = {}", fieldTypes.size());

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

    /**
     * Determines how many multifield items already exist inside the JSON block.
     * <p>
     * Used during JSON update mode to maintain the same number of items.
     *
     * @param json      existing JSON string
     * @param fieldName multifield field name
     * @return number of existing items (default 2)
     */
    private static int getExistingItemCount(String json, String fieldName) {

        log.debug("FILEGEN: Checking existing multifield item count for {}", fieldName);

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

        int result = (max == -1) ? 2 : max + 1;

        log.debug("FILEGEN: Existing item count = {}", result);

        return result;
    }

    /**
     * Syncs JSON content with updated Sling Model fields.
     * <p>
     * Rules:
     * <ul>
     *     <li>Preserve existing values</li>
     *     <li>Add new fields with mock values</li>
     *     <li>Rebuild multifields when structure changes</li>
     * </ul>
     *
     * @param existingJsonString old JSON content
     * @param modelBasePath      model folder
     * @param projectName        project name
     * @param className          model class
     * @param fieldTypes         updated fields
     * @return updated JSON content (pretty printed)
     */
    private static String refreshJsonContent(String existingJsonString,
                                             String modelBasePath,
                                             String projectName,
                                             String className,
                                             Map<String, String> fieldTypes) throws IOException {

        log.info("FILEGEN: Refreshing JSON for model: {}", className);
        log.debug("FILEGEN: Fields to sync = {}", fieldTypes.size());

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

                    Map<String, Object> newMap = mapper.readValue(
                            "{\n" + newJsonBlock + "\n}",
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

    /**
     * Extracts private field declarations from a Sling Model source file.
     *
     * @param content full Java source code of the model
     * @return map of fieldName → fieldType
     */
    private static Map<String, String> extractFieldTypes(String content) {

        log.debug("FILEGEN: Extracting fields from model source ({} chars)", content.length());

        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("private\\s+([\\w<>.,\\s]+)\\s+([a-zA-Z0-9_]+)\\s*;").matcher(content);
        while (m.find()) map.put(m.group(2), m.group(1));

        log.debug("FILEGEN: Total extracted fields = {}", map.size());
        return map;
    }

    /**
     * Generates mock values for any field type used in both JUnit and JSON files.
     *
     * @param type    field type
     * @param field   field name
     * @param forJson true = JSON, false = JUnit mock
     * @return mock value (quoted or raw depending on mode)
     */
    private static String getMockValue(String type, String field, boolean forJson) {

        log.trace("FILEGEN: Generating mock for field={} type={} json={}", field, type, forJson);

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

    /**
     * Builds JSON node for a multifield (List<ChildModel>).
     *
     * @param fieldName     multifield name
     * @param childClass    child model class
     * @param modelBasePath where models are stored
     * @param itemCount     number of multifield items
     * @return JSON block containing items with child fields
     */
    private static String buildMultifieldJson(String fieldName, String childClass,
                                              String modelBasePath, int itemCount) throws IOException {

        log.debug("FILEGEN: Building multifield JSON for {} with {} items", fieldName, itemCount);

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


