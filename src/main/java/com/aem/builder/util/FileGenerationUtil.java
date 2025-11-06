package com.aem.builder.util;

import com.aem.builder.config.ConfigLoader;
import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.model.Enum.FieldType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.w3c.dom.*;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aem.builder.constants.ComponentConstants.*;
import static com.aem.builder.constants.ModelAttributeKeys.PROJECTS_DIR;
import static com.aem.builder.util.JavaFormatterUtil.cleanAndFormatJavaFile;
import static com.aem.builder.util.XmlUtil.formatXml;

/**
 * Utility class for generating AEM component files, dialogs, and models.
 */
@Slf4j
public class FileGenerationUtil {

    /**
     * Generates all files required for a component in the given project.
     *
     * @param projectName The name of the project
     * @param request     ComponentRequest containing component details
     */
    public static void generateAllFiles(String projectName, ComponentRequest request) {
        final String METHOD_PREFIX = "FILEGEN: GENERATEALLFILES -";
        log.info("{} Starting file generation for project: {}", METHOD_PREFIX, projectName);

        try {
            String appsRootPath = PROJECTS_DIR + "/" + projectName + "/" + COMPONENTS_PATH;
            File appsDir = new File(appsRootPath);
            String appName = projectName;
            File[] dirs = appsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (!MSM_FOLDER.equals(dir.getName())) {
                        appName = dir.getName();
                        break;
                    }
                }
            }
            String basePath = appsRootPath + "/" + appName + "/components";
            log.info("{} Components base path: {}", METHOD_PREFIX, basePath);
            Path javaSourceRoot = Paths.get(PROJECTS_DIR + "/" + projectName + "/" + JAVA_SRC_PATH);
            Path modelPath = findModelBasePath(javaSourceRoot);
            if (modelPath == null) {
                log.info("{} No model path found under: {}", METHOD_PREFIX, javaSourceRoot);
                return;
            }
            log.info("{} Model path: {}", METHOD_PREFIX, modelPath);
            String packageName = javaSourceRoot.relativize(modelPath).toString().replace(File.separatorChar, '.');
            log.info("{} Package name: {}", METHOD_PREFIX, packageName);
            generateComponent(basePath, modelPath.toString(), packageName,
                    request.getComponentName(), request.getComponentGroup(),
                    request.getSuperType(), request.getFields());
            log.info("{} Successfully generated all files for project: {}", METHOD_PREFIX, projectName);
        } catch (Exception e) {
            log.info("FILEGEN: GENERATEALLFILES - Error generating files for project: {}", projectName, e);
        }
    }

    /**
     * Helper method to locate the 'models' directory under src/main/java.
     *
     * @param javaSourceRoot Root path to start searching from (usually core/src/main/java)
     * @return Path to the models directory
     * @throws IOException if no models directory is found
     */
    private static Path findModelBasePath(Path javaSourceRoot) throws IOException {
        final String METHOD_PREFIX = "FILEGEN: FINDMODELBASEPATH -";
        log.info("{} Searching for 'models' directory under: {}", METHOD_PREFIX, javaSourceRoot);
        try (Stream<Path> paths = Files.walk(javaSourceRoot)) {
            Optional<Path> modelPath = paths
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("models"))
                    .findFirst();
            if (modelPath.isPresent()) {
                log.info("{} Found 'models' directory at: {}", METHOD_PREFIX, modelPath.get());
                return modelPath.get();
            } else {
                String errorMsg = "Models directory not found under: " + javaSourceRoot;
                log.info("{} {}", METHOD_PREFIX, errorMsg);
                throw new IOException(errorMsg);
            }
        } catch (IOException e) {
            log.info("{} Error while searching for 'models' directory", METHOD_PREFIX, e);
            throw e;
        }
    }

    /**
     * Generates all necessary files and folders for a component.
     * This includes creating the component folder, generating the .content.xml,
     * HTL templates, dialog structure, and the Sling model if applicable.
     */
    public static void generateComponent(String basePath, String modelBasePath, String packageName,
                                         String componentName, String componentGroup, String superType, List<ComponentField> fields) throws Exception {
        final String METHOD_PREFIX = "COMPONENT: GENERATECOMPONENT -";
        log.info("{} Generating component '{}'", METHOD_PREFIX, componentName);
        String componentFolder = basePath + "/" + componentName;
        File folder = new File(componentFolder);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new Exception(METHOD_PREFIX + " Failed to create component folder: " + componentFolder);
        }
        boolean extendsComponent = superType != null && !superType.isBlank();
        boolean hasFields = fields != null && !fields.isEmpty();
        generateComponentContentXml(componentFolder, componentName, componentGroup, superType);
        generateHTL(componentFolder, fields, packageName, componentName, superType);
        if (hasFields) {
            String dialogFolder = componentFolder + "/" + CQ_DIALOG;
            File dialogDir = new File(dialogFolder);
            if (!dialogDir.exists() && !dialogDir.mkdirs()) {
                throw new Exception(METHOD_PREFIX + " Failed to create dialog folder: " + dialogFolder);
            }
            generateDialogContentXml(componentName, dialogFolder, superType, fields);
        }
        if (!extendsComponent || hasFields) {
            generateSlingModel(modelBasePath, packageName, componentName, fields);
        }
        log.info("{} Finished generating component '{}'", METHOD_PREFIX, componentName);
    }

    /**
     * Generates the .content.xml for an AEM component.
     * All namespaces, element names, attributes, and XML declaration
     * are read from a JSON configuration to remove hardcoding.
     * Ensures flexible updates without changing Java code.
     */
    private static void generateComponentContentXml(String folderPath, String componentName,
                                                    String componentGroup, String superType) throws Exception {
        final String METHOD_PREFIX = "CONTENTXML: GENERATECOMPONENTCONTENTXML -";
        log.info("{} Generating .content.xml for component '{}'", METHOD_PREFIX, componentName);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config = mapper.readTree(
                FileGenerationUtil.class.getClassLoader()
                        .getResourceAsStream("component-content-xml.json")
        );
        String xmlDeclaration = config.path("xmlDeclaration").asText();
        String elementName = config.path("elementName").asText();
        String primaryType = config.path("primaryType").asText();
        String defaultGroup = config.path("defaultComponentGroup").asText();
        JsonNode namespaces = config.path("namespaces");
        StringBuilder nsBuilder = new StringBuilder();
        namespaces.fieldNames().forEachRemaining(ns -> {
            nsBuilder.append(" xmlns:").append(ns)
                    .append("=\"").append(namespaces.path(ns).asText()).append("\"");
        });
        JsonNode attributesConfig = config.path("attributes");
        StringBuilder attrBuilder = new StringBuilder();
        attributesConfig.fieldNames().forEachRemaining(attr -> {
            String valueKey = attributesConfig.path(attr).asText();
            String value;
            switch (valueKey) {
                case "componentName" -> value = componentName;
                case "componentGroup" ->
                        value = (componentGroup != null && !componentGroup.isBlank()) ? componentGroup : defaultGroup;
                case "superType" -> value = superType;
                default -> value = "";
            }
            if (value != null && !value.isBlank()) {
                attrBuilder.append("\n    ").append(attr).append("=\"").append(value).append("\"");
            }
        });
        String content = xmlDeclaration + "\n" +
                "<" + elementName + nsBuilder.toString() + " jcr:primaryType=\"" + primaryType + "\"" +
                attrBuilder.toString() + "/>";
        FileUtils.writeStringToFile(new File(folderPath + "/.content.xml"), content, StandardCharsets.UTF_8);
        log.info("{} .content.xml generated at {}/.content.xml", METHOD_PREFIX, folderPath);
    }

    /**
     * Generates the HTL file for an AEM component from JSON templates.
     * Safely loads configuration, resolves placeholders, and writes the output.
     * Handles extending super types and rendering fields with indentation.
     */
    private static void generateHTL(String folderPath, List<ComponentField> fields, String packageName,
                                    String componentName, String superType) throws Exception {
        final String method = "generateHTL";
        log.info("{}: Generating HTL for component '{}'", method, componentName);
        String modelClassName = capitalize(componentName) + "Model";
        StringBuilder sb = new StringBuilder();
        boolean extending = superType != null && !superType.isBlank();
        boolean hasFields = fields != null && !fields.isEmpty();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config;
        try (InputStream is = FileGenerationUtil.class.getClassLoader().getResourceAsStream("component-htl.json")) {
            if (is == null) {
                throw new FileNotFoundException("component-htl.json not found in resources");
            }
            config = mapper.readTree(is);
        } catch (IOException e) {
            log.error("{}: Failed to load component-htl.json", method, e);
            throw e;
        }
        JsonNode declarations = config.path("declarations");
        JsonNode fieldTemplates = config.path("fieldTemplates");
        if (extending && !hasFields) {
            sb.append(resolveTemplate(declarations.path("extendOnly").asText(),
                    packageName, modelClassName, superType, null, null, "model")).append("\n");
        } else {
            sb.append(resolveTemplate(declarations.path("modelUse").asText(),
                    packageName, modelClassName, superType, null, null, "model")).append("\n");
            sb.append(declarations.path("placeholderTemplate").asText()).append("\n");
            sb.append(declarations.path("startTest").asText()).append("\n");
            if (extending) {
                sb.append(resolveTemplate(declarations.path("extendOnly").asText(),
                        packageName, modelClassName, superType, null, null, "model")).append("\n");
            }
            if (hasFields) {
                for (ComponentField field : fields) {
                    appendFieldHTL(sb, field, "model", fieldTemplates, packageName, modelClassName, superType, "  ", 0);
                }
                sb.append(declarations.path("endTest").asText()).append("\n");
                sb.append(declarations.path("placeholderCall").asText()).append("\n");
            }
        }
        try {
            FileUtils.writeStringToFile(
                    new File(folderPath, componentName + ".html"),
                    sb.toString(),
                    StandardCharsets.UTF_8
            );
            log.info("{}: HTL file generated at {}/{}.html", method, folderPath, componentName);
        } catch (IOException e) {
            log.error("{}: Failed to write HTL file for component '{}'", method, componentName, e);
            throw e;
        }
    }

    /**
     * Appends HTL markup for a field using JSON templates.
     * Supports nested rendering for multifields and tabs with indentation.
     * Resolves field type aliases and replaces placeholders dynamically.
     */
    private static void appendFieldHTL(StringBuilder sb, ComponentField field, String modelVar,
                                       JsonNode fieldTemplates, String packageName,
                                       String modelClassName, String superType, String indent, int level) {
        final String method = "appendFieldHTL";
        if (field == null) return;
        String fieldType = field.getFieldType();
        if (fieldType != null) fieldType = fieldType.trim().toLowerCase();
        JsonNode aliases = fieldTemplates.path("aliases");
        if (aliases.has(fieldType)) {
            fieldType = aliases.path(fieldType).asText();
        }
        log.info("{}: Field '{}' of type '{}' resolved to '{}'", method, field.getFieldName(), field.getFieldType(), fieldType);
        if ("tabs".equals(fieldType)) {
            if (field.getNestedFields() != null) {
                for (ComponentField child : field.getNestedFields()) {
                    appendFieldHTL(sb, child, modelVar, fieldTemplates, packageName, modelClassName, superType, indent, level);
                }
            }
            return;
        }
        String fieldName = field.getFieldName();
        String fieldLabel = field.getFieldLabel();
        String templateKey = fieldTemplates.has(fieldType) ? fieldType : "default";
        for (JsonNode line : fieldTemplates.path(templateKey)) {
            String template = line.asText();
            if ("multifield".equals(fieldType) && template.contains("${nestedFields}")) {
                StringBuilder nestedSb = new StringBuilder();
                String nestedVar = "item"; // always use 'item' instead of item0, item1, etc.
                if (field.getNestedFields() != null) {
                    for (ComponentField nested : field.getNestedFields()) {
                        appendFieldHTL(nestedSb, nested, nestedVar, fieldTemplates,
                                packageName, modelClassName, superType,
                                indent + "    ", level + 1);
                    }
                }
                template = template.replace("${nestedFields}", nestedSb.toString());
            }
            String resolved = resolveTemplate(template, packageName, modelClassName, superType, fieldName, fieldLabel, modelVar);
            sb.append(indent).append(resolved).append("\n");
        }
    }

    /**
     * Appends HTL markup for nested fields inside multifield or tab.
     * Delegates rendering back to appendFieldHTL with proper indentation.
     */
    private static void appendNestedHTL(StringBuilder sb, List<ComponentField> nestedFields, String modelVar, String indent,
                                        JsonNode fieldTemplates, String packageName, String modelClassName, String superType) {
        final String method = "appendNestedHTL";
        if (nestedFields == null || nestedFields.isEmpty()) {
            log.debug("{}: No nested fields to append", method);
            return;
        }

        for (ComponentField nested : nestedFields) {
            appendFieldHTL(sb, nested, modelVar, fieldTemplates, packageName, modelClassName, superType, indent, 0);
        }
    }

    /**
     * Replaces placeholders in a template string with actual values.
     * Ensures null-safe substitution and defaults model variable to 'model'.
     */
    private static String resolveTemplate(String template,
                                          String packageName,
                                          String modelClassName,
                                          String superType,
                                          String fieldName,
                                          String fieldLabel,
                                          String modelVar) {
        final String method = "resolveTemplate";
        if (template == null) {
            log.warn("{}: Null template encountered", method);
            return "";
        }
        return template.replace("${packageName}", packageName != null ? packageName : "")
                .replace("${modelClassName}", modelClassName != null ? modelClassName : "")
                .replace("${superType}", superType != null ? superType : "")
                .replace("${fieldName}", fieldName != null ? fieldName : "")
                .replace("${fieldLabel}", fieldLabel != null ? fieldLabel : "")
                .replace("${modelVar}", modelVar != null ? modelVar : "model");
    }


    /**
     * Resolves placeholders inside a template line.
     * Supported placeholders: ${packageName}, ${modelClassName}, ${superType}, ${fieldName}, ${fieldLabel}.
     *
     * @param template       The template string containing placeholders
     * @param packageName    Java package name
     * @param modelClassName Model class name
     * @param superType      Super-type (if any)
     * @param fieldName      Field name
     * @param fieldLabel     Field label
     * @return Resolved string with placeholders replaced
     */
    private static String resolveTemplate(String template,
                                          String packageName,
                                          String modelClassName,
                                          String superType,
                                          String fieldName,
                                          String fieldLabel) {
        if (template == null) return "";
        return template.replace("${packageName}", packageName != null ? packageName : "")
                .replace("${modelClassName}", modelClassName != null ? modelClassName : "")
                .replace("${superType}", superType != null ? superType : "")
                .replace("${fieldName}", fieldName != null ? fieldName : "")
                .replace("${fieldLabel}", fieldLabel != null ? fieldLabel : "");
    }

    /**
     * Generates the cq:dialog .content.xml for an AEM component.
     * <p>
     * Loads XML templates from JSON, replaces placeholders with component data,
     * and writes a properly formatted dialog XML file with pretty indentation.
     */
    public static void generateDialogContentXml(String componentName, String dialogFolder,
                                                String superType, List<ComponentField> fields) {
        if (fields == null || fields.isEmpty()) {
            log.info("DIALOG: Skipping dialog generation for component '{}' as no fields defined", componentName);
            return;
        }
        log.info("DIALOG: Starting dialog generation for component '{}'", componentName);
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("dialog-content-xml.json")) {
            if (is == null) throw new FileNotFoundException("dialog-content-xml.json not found in resources");
            JSONObject config = new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            String dialogTitle = componentName + " Dialog";
            String superTypeAttr = "";
            if (superType != null && !superType.isBlank()) {
                superTypeAttr = config.getJSONObject("declarations")
                        .getString("superTypeAttr")
                        .replace("${superType}", superType);
            }
            List<ComponentField> tabFields = new ArrayList<>();
            List<ComponentField> nonTabFields = new ArrayList<>();
            for (ComponentField f : fields) {
                if ("tabs".equalsIgnoreCase(f.getFieldType())) {
                    tabFields.add(f);
                    log.info("DIALOG: Found tab field '{}'", f.getFieldName());
                } else {
                    nonTabFields.add(f);
                    log.info("DIALOG: Found non-tab field '{}'", f.getFieldName());
                }
            }
            StringBuilder sb = new StringBuilder();
            String header = config.getJSONObject("declarations").getString("header")
                    .replace("${dialogTitle}", dialogTitle)
                    .replace("${superTypeAttr}", superTypeAttr);
            sb.append(header).append("\n");
            if (tabFields.isEmpty()) {
                log.info("DIALOG: No tabs found. Generating flat dialog for '{}'", componentName);
                String flatStart = config.getJSONObject("containers").getString("flatStart")
                        .replace("${layout}", config.getJSONObject("layouts").getString("flat"));
                sb.append(flatStart).append("\n");
                for (ComponentField f : nonTabFields) {
                    log.info("DIALOG: Generating field '{}' in flat dialog", f.getFieldName());
                    sb.append(generateFieldXml(safeNodeName(f.getFieldName(), "field"), f));
                }
                sb.append(config.getJSONObject("containers").getString("flatEnd")).append("\n");
                sb.append(config.getJSONObject("declarations").getString("footer")).append("\n");

            } else {
                log.info("DIALOG: Tabs detected. Generating tabbed dialog for '{}'", componentName);
                ComponentField explicitMainTab = tabFields.stream()
                        .filter(tf -> "main".equalsIgnoreCase(safeNodeName(tf.getFieldName(), "tab")) ||
                                (tf.getFieldLabel() != null && tf.getFieldLabel().trim().equalsIgnoreCase("Main")))
                        .findFirst().orElse(null);
                boolean willAutoCreateMain = explicitMainTab == null && !nonTabFields.isEmpty();
                String tabsStart = config.getJSONObject("containers").getString("tabsStart")
                        .replace("${layout}", config.getJSONObject("layouts").getString("tabs"));
                sb.append(tabsStart).append("\n");
                Set<String> writtenTabs = new HashSet<>();
                for (ComponentField tabField : tabFields) {
                    String tabNodeName = safeNodeName(tabField.getFieldName(), "tab");
                    String tabTitle = (tabField.getFieldLabel() != null) ? tabField.getFieldLabel() : tabNodeName;
                    if (!writtenTabs.add(tabNodeName.toLowerCase())) {
                        log.warn("DIALOG: Duplicate tab '{}' skipped", tabNodeName);
                        continue;
                    }
                    log.info("DIALOG: Processing tab '{}'", tabNodeName);
                    if (tabField.isParentTab()) {
                        try {
                            String projectName = superType.split("/")[2];
                            String parentTabXml = extractTabsOnly(superType, projectName, List.of(tabTitle));
                            log.info("tabs........,{}", parentTabXml);
                            if (parentTabXml != null && !parentTabXml.isBlank()) {
                                log.info("DIALOG: Extracted parent tab XML for '{}'", tabNodeName);
                                StringBuilder tabBuilder = new StringBuilder(parentTabXml);
                                int insertPos = findInnermostItems(tabBuilder, 0);
                                StringBuilder fieldsBuilder = new StringBuilder();
                                if (tabField.getNestedFields() != null) {
                                    for (ComponentField nf : tabField.getNestedFields()) {
                                        fieldsBuilder.append(generateFieldXml(safeNodeName(nf.getFieldName(), "field"), nf));
                                    }
                                }
                                if (explicitMainTab == tabField && !nonTabFields.isEmpty()) {
                                    for (ComponentField f : nonTabFields) {
                                        fieldsBuilder.append(generateFieldXml(safeNodeName(f.getFieldName(), "field"), f));
                                    }
                                }
                                tabBuilder.insert(insertPos, fieldsBuilder.toString());
                                sb.append(tabBuilder);
                                continue;
                            }
                        } catch (Exception e) {
                            log.warn("DIALOG: Could not extract parent tab '{}' from superType '{}': {}", tabTitle, superType, e.getMessage());
                        }
                    }
                    StringBuilder fieldsBuilder = new StringBuilder();
                    if (tabField.getNestedFields() != null) {
                        for (ComponentField nf : tabField.getNestedFields()) {
                            fieldsBuilder.append(generateFieldXml(safeNodeName(nf.getFieldName(), "field"), nf));
                        }
                    }
                    if (explicitMainTab == tabField && !nonTabFields.isEmpty()) {
                        for (ComponentField f : nonTabFields) {
                            fieldsBuilder.append(generateFieldXml(safeNodeName(f.getFieldName(), "field"), f));
                        }
                    }
                    String tabXml = config.getString("tabTemplate")
                            .replace("${tabNodeName}", tabNodeName)
                            .replace("${tabTitle}", tabTitle)
                            .replace("${resourceType}", getResourceType(tabField.getFieldType()))
                            .replace("${fields}", fieldsBuilder.toString());
                    sb.append(tabXml).append("\n");
                }
                if (willAutoCreateMain) {
                    log.info("DIALOG: Auto-creating 'Main' tab for non-tab fields");
                    String autoXml = config.getString("autoMainTabTemplate")
                            .replace("${tabNodeName}", "main")
                            .replace("${resourceType}", getResourceType("tabs"))
                            .replace("${fields}", nonTabFields.stream()
                                    .map(f -> generateFieldXml(safeNodeName(f.getFieldName(), "field"), f))
                                    .collect(Collectors.joining()));
                    sb.append(autoXml).append("\n");
                }
                sb.append(config.getJSONObject("containers").getString("tabsEnd")).append("\n");
                sb.append(config.getJSONObject("declarations").getString("footer")).append("\n");
            }
            File out = new File(dialogFolder, ".content.xml");
            FileUtils.writeStringToFile(out, formatXml(sb.toString()), StandardCharsets.UTF_8);
            log.info("DIALOG: Dialog .content.xml successfully generated at {}", out.getAbsolutePath());
        } catch (Exception e) {
            log.error("DIALOG: Error generating dialog for '{}': {}", componentName, e.getMessage(), e);
        }
    }

    /**
     * Utility: safe XML node name
     */
    private static String safeNodeName(String fieldName, String fallback) {
        if (fieldName == null || fieldName.isBlank()) {
            return fallback;
        }
        String sanitized = fieldName.replaceAll("[^a-zA-Z0-9_-]", ""); // letters, digits, underscore, hyphen
        return sanitized.isEmpty() ? fallback : sanitized;
    }


/**
 * Generates XML for a single dialog field (JSON-driven, matches original switch-case logic 100%).
 */
    /**
     * Generates XML for a single dialog field (JSON-driven, switch-case equivalent).
     */
    private static String generateFieldXml(String nodeName, ComponentField field) {
        String type = field.getFieldType().toLowerCase();
        String label = field.getFieldLabel();
        String name = field.getFieldName();
        List<OptionItem> options = field.getOptions();
        List<ComponentField> nested = field.getNestedFields();

        // Prevent nested tabs
        if (nested != null) {
            for (ComponentField nf : nested) {
                if ("tabs".equalsIgnoreCase(nf.getFieldType())) {
                    throw new IllegalArgumentException(
                            "Nested tabs are not allowed inside field: " + name + " (" + label + ")");
                }
            }
        }

        // Sanitize nodeName
        if (nodeName == null || nodeName.isBlank()) {
            nodeName = type.replaceAll("\\W+", "");
        } else {
            nodeName = nodeName.replaceAll("\\W+", "");
        }

        // Canonicalize for resourceType
        String resourceLookupType = "multiselect".equals(type) ? "select" : type;

        // Load JSON templates
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try (InputStream is = FileGenerationUtil.class.getResourceAsStream("/component-fields.json")) {
            if (is == null) throw new RuntimeException("component-fields.json not found!");
            root = mapper.readTree(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read component-fields.json", e);
        }

        // Find template
        JsonNode templateNode = null;
        for (JsonNode f : root.get("fields")) {
            if (type.equalsIgnoreCase(f.get("type").asText())) {
                templateNode = f;
                break;
            }
        }
        if (templateNode == null) {
            System.err.println("Unsupported AEM field type: " + type);
            return "";
        }

        // Load template XML
        String xml = templateNode.get("xml").asText();

        // Replace placeholders
        java.util.function.Function<String, String> esc = s -> {
            if (s == null) return "";
            return s.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        };

        xml = xml.replace("${nodeName}", nodeName)
                .replace("${resourceType}", esc.apply(getResourceType(resourceLookupType)))
                .replace("${label}", esc.apply(label))
                .replace("${name}", esc.apply(name));

        // Handle options for select/multiselect/radiogroup
        if (xml.contains("${options}")) {
            StringBuilder sbOptions = new StringBuilder();
            if (options != null && !options.isEmpty()) {
                for (int i = 0; i < options.size(); i++) {
                    OptionItem opt = options.get(i);
                    sbOptions.append("      <option").append(i + 1).append("\n")
                            .append("        jcr:primaryType=\"nt:unstructured\"\n")
                            .append("        text=\"").append(esc.apply(opt.getText())).append("\"\n")
                            .append("        value=\"").append(esc.apply(opt.getValue())).append("\"/>\n");
                }
            }
            xml = xml.replace("${options}", sbOptions.toString());
        }

        // Handle nested fields (multifield, tabs)
        if (xml.contains("${nestedFields}")) {
            StringBuilder sbNested = new StringBuilder();
            if (nested != null && !nested.isEmpty()) {
                for (ComponentField nf : nested) {
                    String subNode = (nf.getFieldName() != null)
                            ? nf.getFieldName().replaceAll("\\W+", "")
                            : nf.getFieldType();
                    sbNested.append(generateFieldXml(subNode, nf));
                }
            }
            xml = xml.replace("${nestedFields}", sbNested.toString());
        }

        return xml;
    }


    /**
     * Returns the resource type for a given field type.
     */
    public static String getResourceType(String type) {
        return FieldType.getTypeResourceMap().getOrDefault(type.toLowerCase(), "");
    }


    /**
     * Generates a Sling Model Java class for the given component.
     * <p>
     * Responsibilities:
     * - Creates the Java file
     * - Iterates through fields and generates them
     * - Injects an {@code isEmpty()} method for HTL checks
     * </p>
     *
     * @param modelBasePath output folder path
     * @param packageName   Java package
     * @param componentName component name (used for class name)
     * @param fields        list of dialog fields
     */
    private static void generateSlingModel(String modelBasePath, String packageName,
                                           String componentName, List<ComponentField> fields) throws Exception {


        log.info("{} Generating Sling Model for component '{}'", MODEL_GEN_PREFIX, componentName);


        String className = capitalize(componentName) + MODEL_SUFFIX;
        File modelDir = new File(modelBasePath);
        modelDir.mkdirs();


        Map<String, Object> config = ConfigLoader.loadConfig("slingmodel.json");


        StringBuilder sb = new StringBuilder();
        sb.append(((Map<String, String>) config.get("classTemplate")).get("package").replace("{packageName}", packageName))
                .append("\n\n")
                .append(IMPORTS_BLOCK)
                .append(MODEL_ANNOTATION)
                .append(((Map<String, String>) config.get("classTemplate")).get("classDeclaration").replace("{className}", className))
                .append("\n\n");


        List<ComponentField> generatedFields = addFieldsToModel(sb, modelBasePath, packageName, componentName, fields);
        log.info("{} Fields processed: {}", MODEL_GEN_PREFIX, generatedFields);


        // isEmpty() method
        Map<String, String> methods = (Map<String, String>) ((Map<String, Object>) config.get("methods")).get("isEmpty");
        sb.append("    ").append(methods.get("javadoc")).append("\n")
                .append("    ").append(methods.get("signature")).append("\n")
                .append("        ").append(methods.get("bodyStart")).append("\n");


        Map<String, String> fieldChecks = (Map<String, String>) config.get("fieldChecks");
        for (ComponentField field : generatedFields) {
            String type = field.getFieldType().toLowerCase();
            String check = fieldChecks.getOrDefault(type, fieldChecks.get("default"));
            sb.append("        ").append(check.replace("{name}", field.getFieldName())).append("\n");
        }


        sb.append("        return empty;\n")
                .append("    }\n")
                .append(((Map<String, String>) config.get("classTemplate")).get("closingBrace"));


        FileUtils.writeStringToFile(new File(modelBasePath, className + JAVA_EXTENSION),
                sb.toString(), StandardCharsets.UTF_8);


        log.info("{} Sling Model generated at {}/{}.java", MODEL_GEN_PREFIX, modelBasePath, className);


        // ----------------------------------------------------------
        // AUTO-GENERATE JUNIT TEST FOR THIS MODEL
        // ----------------------------------------------------------
        try {
            generateJUnitTestForModel(modelBasePath, packageName, className, fields);
        } catch (Exception e) {
            log.warn("{} Failed to generate JUnit test for {}", MODEL_GEN_PREFIX, className, e);
        }
    }

    /**
     * Recursively adds fields to the Sling Model class.
     * - Handles multifield, checkbox, textfield, numberfield, tagfield, multiselect
     * - Skips "tabs" node but still processes its child fields
     *
     * @param sb            StringBuilder to append generated model code
     * @param modelBasePath Path where generated model class files will be saved
     * @param packageName   Package name for the generated model class
     * @param componentName Component name for logging
     * @param fields        List of ComponentField objects representing component fields
     * @return List of fields that were generated in the model (used for isEmpty() check)
     * @throws Exception If file generation fails
     */
    private static List<ComponentField> addFieldsToModel(StringBuilder sb, String modelBasePath, String packageName,
                                                         String componentName, List<ComponentField> fields) throws Exception {


        List<ComponentField> generatedFields = new ArrayList<>();


        if (fields == null || fields.isEmpty()) {
            log.info("MODEL: No fields found for component '{}'", componentName);
            return generatedFields;
        }


        for (ComponentField field : fields) {
            String name = field.getFieldName();
            String type = field.getFieldType().toLowerCase();


            // Handle "tabs": skip the tab itself, but process its children
            if (TYPE_TABS.equals(type)) {
                log.info("MODEL: Skipping tab '{}' but processing nested fields", name);
                List<ComponentField> nestedFields = addFieldsToModel(sb, modelBasePath, packageName, componentName, field.getNestedFields());
                if (nestedFields != null) generatedFields.addAll(nestedFields);
                continue;
            }


            log.info("MODEL: Adding field '{}' of type '{}'", name, type);


            // Generate code based on field type
            switch (type) {
                case TYPE_MULTIFIELD -> {
                    generateChildModelClass(modelBasePath, packageName, componentName, field);
                    sb.append("    ").append(CHILD_RESOURCE_ANNOTATION)
                            .append("\n    private List<").append(capitalize(name)).append("> ").append(name).append(";\n\n");
                }
                case TYPE_CHECKBOX -> sb.append("    ").append(VALUE_MAP_ANNOTATION)
                        .append("\n    private boolean ").append(name).append(";\n\n");
                case TYPE_MULTISELECT, TYPE_TAGFIELD -> sb.append("    ").append(VALUE_MAP_ANNOTATION)
                        .append("\n    private List<String> ").append(name).append(";\n\n");
                case TYPE_NUMBERFIELD -> sb.append("    ").append(VALUE_MAP_ANNOTATION)
                        .append("\n    private double ").append(name).append(";\n\n");
                default -> sb.append("    ").append(VALUE_MAP_ANNOTATION)
                        .append("\n    private String ").append(name).append(";\n\n");
            }


            // Add getter method
            /* addGetter(sb, field);*/


            // Suppose you already loaded the config in generateSlingModel
            Map<String, Object> config = ConfigLoader.loadConfig("slingmodel.json");


// Inside addFieldsToModel
            addGetter(sb, field, config);


            // Add to generated list for isEmpty() check
            generatedFields.add(field);
        }


        return generatedFields;
    }

    /**
     * Generates a child Sling Model class for a multifield component.
     * <p>
     * Responsibilities:
     * - Creates the child model Java file
     * - Iterates through nested fields
     * - Skips "tabs" but processes their children
     * </p>
     *
     * @param modelBasePath       Output folder path
     * @param packageName         Java package for the model
     * @param parentComponentName Parent component name for logging
     * @param parentField         The multifield ComponentField
     * @throws Exception If file creation fails
     */
    private static void generateChildModelClass(String modelBasePath, String packageName,
                                                String parentComponentName, ComponentField parentField) throws Exception {


        String className = capitalize(parentField.getFieldName());
        log.info("{} Generating Child Model for multifield '{}' in component '{}'",
                MODEL_GEN_PREFIX, parentField.getFieldName(), parentComponentName);


        Map<String, Object> config = ConfigLoader.loadConfig("slingmodel.json");


        StringBuilder sb = new StringBuilder();
        Map<String, String> classTemplate = (Map<String, String>) config.get("classTemplate");


        sb.append(classTemplate.get("package").replace("{packageName}", packageName)).append("\n\n")
                .append(IMPORTS_BLOCK)
                .append(MODEL_ANNOTATION)
                .append(classTemplate.get("classDeclaration").replace("{className}", className))
                .append("\n\n");


        // Recursively add fields from multifield
        List<ComponentField> generatedFields = addFieldsToModel(sb, modelBasePath, packageName,
                parentComponentName, parentField.getNestedFields());


        sb.append(classTemplate.get("closingBrace"));


        File file = new File(modelBasePath, className + JAVA_EXTENSION);
        FileUtils.writeStringToFile(file, sb.toString(), StandardCharsets.UTF_8);


        log.info("{} Child Model '{}' generated at {}/{}.java with {} fields",
                MODEL_GEN_PREFIX, className, modelBasePath, className, generatedFields.size());

        // ----------------------------------------------------------
        // AUTO-GENERATE JUNIT TEST FOR MULTIFIELD MODEL
        // ----------------------------------------------------------
        try {
            generateJUnitTestForModel(modelBasePath, packageName, className, generatedFields);
            log.info("{} JUnit Test generated for multifield model '{}'", MODEL_GEN_PREFIX, className);
        } catch (Exception e) {
            log.warn("{} Failed to generate JUnit test for multifield '{}'", MODEL_GEN_PREFIX, className, e);
        }

    }


    /**
     * Generates the getter method for a given component field.
     * <p>
     * Fully driven by JSON templates (getterTemplates).
     * </p>
     *
     * @param sb     StringBuilder to append the getter code
     * @param field  ComponentField for which the getter is generated
     * @param config JSON configuration map containing getter templates
     */
    private static void addGetter(StringBuilder sb, ComponentField field, Map<String, Object> config) {
        String name = field.getFieldName();
        String cap = capitalize(name);
        String type = field.getFieldType().toLowerCase();


        Map<String, String> getterTemplates = (Map<String, String>) config.get("getterTemplates");
        String template = getterTemplates.getOrDefault(type, getterTemplates.get("default"));


        String getterCode = template.replace("{name}", name).replace("{cap}", cap).replace("{type}", cap);
        sb.append("    ").append(getterCode).append("\n\n");


        log.debug("{} Getter generated for field '{}' of type '{}'", MODEL_GEN_PREFIX, name, type);
    }


    /**
     * Capitalizes the first letter of the input string.
     */
    private static String capitalize(String input) {
        return input == null || input.isEmpty() ? input : input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Reads the contents of a file as a String.
     */
    public static String readFile(File file) {
        try {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * CHECK_MODEL_FILE_EXISTS: Checks if a Java model class exists for a given multifield in a project.
     *
     * @param projectName    The project name.
     * @param multifieldName The name of the multifield to check.
     * @return true if the corresponding Java class exists, false otherwise.
     */
    public static boolean checkModelFileExists(String projectName, String multifieldName) {
        final String METHOD_PREFIX = "CHECKMODELFILEEXISTS";
        try {
            log.info("{}: Checking model file for multifield '{}' in project '{}'", METHOD_PREFIX, multifieldName, projectName);
            Path javaSourceRoot = Paths.get(System.getProperty("user.dir"))
                    .resolve(PROJECTS_DIR)
                    .resolve(projectName)
                    .resolve(JAVA_SRC_PATH);
            log.info("{}: Java source root resolved to '{}'", METHOD_PREFIX, javaSourceRoot);
            Path modelPath = findModelBasePath(javaSourceRoot);
            if (modelPath == null || !Files.exists(modelPath)) {
                log.warn("{}: Model path '{}' does not exist", METHOD_PREFIX, modelPath);
                return false;
            }
            log.info("{}: Model path resolved to '{}'", METHOD_PREFIX, modelPath);
            String className = capitalize(multifieldName);
            String expectedFileName = className + ".java";
            log.info("{}: Expected model file name '{}'", METHOD_PREFIX, expectedFileName);
            try (Stream<Path> filesStream = Files.list(modelPath)) {
                boolean exists = filesStream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .anyMatch(fileName -> fileName.equals(expectedFileName));
                log.info("{}: Model file '{}' exists: {}", METHOD_PREFIX, expectedFileName, exists);
                return exists;
            }
        } catch (IOException e) {
            log.error("{}: IOException while checking model file for '{}': {}", METHOD_PREFIX, multifieldName, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("{}: Unexpected error while checking model file for '{}': {}", METHOD_PREFIX, multifieldName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts the tab structure from a given AEM component's dialog and its super types recursively.
     * This method allows filtering specific tabs and avoids infinite recursion by keeping track of visited super types.
     * It also handles core super types differently based on the CORE_PREFIX.
     */
    public static String extractTabsOnly(String superType, String projectName, List<String> filterTabs) throws Exception {
        String method = "extractTabsOnly";
        log.info("[{}] Called with superType='{}', projectName='{}', filterTabs={}", method, superType, projectName, filterTabs);
        StringBuilder sb = new StringBuilder();
        Set<String> visitedSuperTypes = new HashSet<>();
        collectTabsRecursive(superType, projectName, sb, 0, superType.startsWith(CORE_PREFIX), filterTabs, visitedSuperTypes);
        log.info("[{}] Final extracted tabs structure:\n{}", method, sb.toString());
        return sb.toString().trim();
    }

    /**
     * Recursively collects tabs from the specified component super type and its parent super types.
     * Keeps track of visited super types to prevent infinite recursion and handles core super types differently.
     */
    private static void collectTabsRecursive(String superType, String projectName,
                                             StringBuilder sb, int indent,
                                             boolean stopAtCore,
                                             List<String> filterTabs,
                                             Set<String> visitedSuperTypes) throws Exception {
        String method = "collectTabsRecursive";
        if (visitedSuperTypes.contains(superType)) {
            log.info("[{}] Already visited superType '{}', skipping recursion.", method, superType);
            return;
        }
        visitedSuperTypes.add(superType);
        boolean isCore = superType.startsWith(CORE_PREFIX);
        String basePath;
        if (isCore) {
            basePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", superType).toString();
        } else {
            basePath = System.getProperty("user.dir") + "/" + PROJECTS_DIR
                    + "/" + projectName + "/" + CONTENT_ROOT_PATH + "/" + superType;
        }
        File dialogFile = new File(basePath + "/" + DIALOG_FILE);
        log.info("[{}] Looking for dialog file at '{}'", method, dialogFile.getAbsolutePath());
        if (!dialogFile.exists()) {
            log.warn("[{}] Dialog file does not exist at '{}'", method, dialogFile.getAbsolutePath());
            return;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(dialogFile);
        Node root = doc.getDocumentElement();
        log.info("[{}] Processing root node '{}' for superType '{}'", method, root.getNodeName(), superType);
        extractRequestedTabs(root, sb, indent, filterTabs);
        Node superTypeAttr = root.getAttributes().getNamedItem(SLING_RESOURCE_SUPER_TYPE);
        if (superTypeAttr != null) {
            String parentSuperType = superTypeAttr.getNodeValue();
            if (parentSuperType != null && !parentSuperType.isBlank()) {
                boolean parentIsCore = parentSuperType.startsWith(CORE_PREFIX);
                log.info("[{}] Recursing to parent superType '{}', parentIsCore={}, stopAtCore={}", method, parentSuperType, parentIsCore, stopAtCore);
                if (!parentIsCore || !stopAtCore) {
                    collectTabsRecursive(parentSuperType, projectName, sb, indent, parentIsCore, filterTabs, visitedSuperTypes);
                }
            }
        }
    }


    /**
     * Recursively extracts requested tab nodes from a JCR node tree, builds their skeletons,
     * and appends them to the provided StringBuilder.
     *
     * @param node       The current JCR Node being inspected
     * @param sb         StringBuilder to append the tab skeletons
     * @param indent     Current indentation level (for future formatting)
     * @param filterTabs List of tab titles to include; if null or empty, all tabs are included
     */
    private static void extractRequestedTabs(Node node, StringBuilder sb, int indent, List<String> filterTabs) {
        final String METHOD = "EXTRACT_REQUESTED_TABS";
        if (node.getNodeType() != Node.ELEMENT_NODE) return;
        NamedNodeMap attrs = node.getAttributes();
        String tabTitle = (attrs != null && attrs.getNamedItem("jcr:title") != null)
                ? attrs.getNamedItem("jcr:title").getNodeValue()
                : null;
        boolean isTabCandidate = tabTitle != null;
        if (isTabCandidate) {
            boolean isRequested = filterTabs == null || filterTabs.isEmpty() ||
                    filterTabs.stream().anyMatch(f -> f.equalsIgnoreCase(tabTitle));
            log.info("{}: Node '{}' is a tab candidate, isRequested={}", METHOD, tabTitle, isRequested);
            if (isRequested) {
                String skeleton = buildTabSkeleton(node);
                log.info("{}: Built tab skeleton for '{}'", METHOD, tabTitle);
                sb.append(skeleton).append("\n");
                return;
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            extractRequestedTabs(children.item(i), sb, indent, filterTabs);
        }
    }

    /**
     * Builds a skeleton XML string for a tab node, excluding all field nodes.
     *
     * @param tabNode The tab Node to process
     * @return The XML string skeleton of the tab
     */
    private static String buildTabSkeleton(Node tabNode) {
        final String METHOD = "BUILD_TAB_SKELETON";

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document newDoc = builder.newDocument();
            Node copied = copyWithoutFields(tabNode, newDoc);
            if (copied != null) {
                newDoc.appendChild(copied);
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(newDoc), new StreamResult(writer));
            log.info("{}: Successfully built skeleton for node '{}'", METHOD, tabNode.getNodeName());
            return writer.getBuffer().toString();

        } catch (Exception e) {
            log.error("{}: Failed to build tab skeleton for node '{}': {}", METHOD, tabNode.getNodeName(), e.getMessage(), e);
            return "<error>Failed to build tab skeleton: " + e.getMessage() + "</error>";
        }
    }

    /**
     * Recursively copies a JCR node and its children, excluding all recognized field nodes.
     *
     * @param node      The Node to copy
     * @param targetDoc The target Document for node import
     * @return The copied Node without fields, or null if the node itself is a field
     */
    private static Node copyWithoutFields(Node node, Document targetDoc) {
        final String METHOD = "COPY_WITHOUT_FIELDS";
        if (isFieldNode(node)) {
            log.debug("{}: Skipping field node '{}'", METHOD, node.getNodeName());
            return null;
        }
        Node newNode = targetDoc.importNode(node, false);
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childCopy = copyWithoutFields(children.item(i), targetDoc);
            if (childCopy != null) {
                newNode.appendChild(childCopy);
            }
        }
        log.debug("{}: Copied node '{}'", METHOD, node.getNodeName());
        return newNode;
    }

    /**
     * Utility method to determine whether a given JCR Node represents a field in the AEM dialog.
     * Excludes TABS containers explicitly.
     *
     * @param node The JCR Node to evaluate
     * @return true if the node is a field type, false otherwise
     */
    private static boolean isFieldNode(Node node) {
        final String METHOD = "IS_FIELD_NODE";
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            log.debug("{}: Node '{}' is not an element node", METHOD, node.getNodeName());
            return false;
        }
        NamedNodeMap attrs = node.getAttributes();
        if (attrs == null) {
            log.debug("{}: Node '{}' has no attributes", METHOD, node.getNodeName());
            return false;
        }
        Node resTypeNode = attrs.getNamedItem("sling:resourceType");
        if (resTypeNode == null) {
            log.debug("{}: Node '{}' has no sling:resourceType", METHOD, node.getNodeName());
            return false;
        }
        String type = resTypeNode.getNodeValue();
        if (FieldType.TABS.getResourceType().equals(type)) {
            log.debug("{}: Node '{}' is a TABS container, skipping", METHOD, node.getNodeName());
            return false;
        }
        Map<String, String> fieldMap = FieldType.getTypeResourceMap();
        boolean isField = fieldMap.values().stream()
                .filter(rt -> !rt.equals(FieldType.TABS.getResourceType()))
                .anyMatch(rt -> type.contains(rt)) ||
                type.contains(WELL) ||
                type.contains(TEXT) ||
                type.contains(INCLUDE);
        if (isField) {
            log.debug("{}: Node '{}' detected as field type '{}'", METHOD, node.getNodeName(), type);
        } else {
            log.debug("{}: Node '{}' is not a recognized field type", METHOD, node.getNodeName());
        }
        return isField;
    }

    /**
     * Finds the deepest nested <items> position in a given XML string.
     *
     * <p>
     * This method is used to determine the correct insertion point for new fields
     * in AEM dialog XML structures. It handles nested <items> blocks, commonly
     * found in tabs, containers, or complex dialogs.
     * </p>
     */
    private static int findInnermostItems(StringBuilder xml, int startPos) {
        final String METHOD = "FIND_INNERMOST_ITEMS";
        JSONObject config;
        // Load JSON config directly from resources
        try (InputStream is = FileGenerationUtil.class.getClassLoader().getResourceAsStream("items.json")) {
            if (is == null) {
                throw new FileNotFoundException("Config file not found in resources: items.json");
            }
            config = new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            log.info("{}: Successfully loaded items configuration", METHOD);
        } catch (Exception e) {
            log.error("{}: Failed to load config - {}", METHOD, e.getMessage(), e);
            return xml.length(); // fallback
        }
        // Get opening and closing tags from JSON
        String itemsTag = config.getString("itemsTag");
        String itemsCloseTag = config.getString("itemsCloseTag");
        int pos = startPos;
        int deepestPos = -1;
        // Traverse all <items> blocks to find the deepest one
        while (true) {
            int openTag = xml.indexOf(itemsTag, pos);
            if (openTag == -1) break;
            int endOfOpen = xml.indexOf(">", openTag) + 1;
            int depth = 1;
            int searchPos = endOfOpen;
            // Handle nested <items> blocks
            while (depth > 0) {
                int nextOpen = xml.indexOf(itemsTag, searchPos);
                int nextClose = xml.indexOf(itemsCloseTag, searchPos);
                if (nextClose == -1) break;
                if (nextOpen != -1 && nextOpen < nextClose) {
                    depth++;
                    searchPos = nextOpen + 1;
                } else {
                    depth--;
                    searchPos = nextClose + 1;
                }
            }
            deepestPos = endOfOpen;
            pos = endOfOpen;
        }

        log.info("{}: Deepest <items> position found at {}", METHOD, deepestPos);
        return deepestPos != -1 ? deepestPos : xml.length();
    }


// -------------------- JUnit files Generation --------------------

    /**
     * Generates a basic JUnit test class for a Sling Model, including dynamic field tests.
     *
     * @param modelBasePath base output folder path (e.g., src/main/java/com/example/models)
     * @param packageName   package name of the model
     * @param className     name of the generated Sling Model class
     * @param fields        list of fields defined in the Sling model
     */
    private static void generateJUnitTestForModel(
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

        // --- Build dynamic field test methods ---
        StringBuilder fieldTests = new StringBuilder();
        for (ComponentField field : fields) {
            String fieldName = field.getFieldName();
            String getterName = "get" + capitalize(fieldName);
            fieldTests.append("    @Test\n")
                    .append("    void test").append(capitalize(fieldName)).append("() {\n")
                    .append("        assertNotNull(model.").append(getterName).append("(), ")
                    .append("\"").append(fieldName).append(" should not be null\");\n")
                    .append("    }\n\n");
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

// Add dummy values for each model field
        for (ComponentField field : fields) {
            resourceProps.append(", \"").append(field.getFieldName()).append("\", \"Test")
                    .append(capitalize(field.getFieldName())).append("\"");
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

    //-------------------- update files --------------------

    public static void updateAllFiles(String projectName, ComponentRequest request) {
        log.info("FILEGEN: Starting file update for project: {}", projectName);
        try {
            String appsRoot = "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps";
            File appsDir = new File(appsRoot);

            String appName = projectName;

            File[] dirs = appsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (!"msm".equals(dir.getName())) {
                        appName = dir.getName(); // Found a valid app folder
                        break;
                    }
                }
            }

            String basePath = appsRoot + "/" + appName + "/components/";

            Path javaSourceRoot = Paths.get("generated-projects/" + projectName + "/core/src/main/java/");

            // Find models directory
            Path modelPath = findModelBasePath(javaSourceRoot);
            log.info("ModelPath {}", modelPath);

            String modelBasePath = modelPath.toString();
            log.info("ModelBasePath {}", modelBasePath);

            String packageName = javaSourceRoot.relativize(modelPath).toString().replace(File.separatorChar, '.');
            log.info("PackageName {}", packageName);

            // ------------------- call update methods -------------------
            updateContentXml(projectName, request);
            updateComponent(projectName, request, packageName);

            log.info("FILEGEN: Successfully updated all files for project: {}", projectName);
        } catch (Exception e) {
            log.error("FILEGEN: Error updating files for project: {}", projectName, e);
            e.printStackTrace();
        }
    }

     /* public static void updateComponent(String projectName, ComponentRequest request, String packageName) throws Exception {
        log.info("FILEGEN: updateComponent method called !!! " );
        updateDialog(projectName, request);        // update .content.xml of dialog
        *//*String dialogPath = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components/" + request.getComponentName() + "/_cq_dialog/";
        updateDialogContentXml(
                request.getComponentName(),      // componentName
                dialogPath, // dialogFolder path
                request.getSuperType(),          // superType
                request.getFields(),             // List<ComponentField>
                projectName                      // projectName
        );*//*
        updateSlingModel(request);
        updateHTL(projectName, request, packageName);// update Sling Model (and HTL inside)
        log.info("FILEGEN: Component '{}' updated successfully", request.getComponentName());
    }*/


    public static void updateComponent(String projectName, ComponentRequest request, String packageName) throws Exception {
        log.info("FILEGEN: updateComponent method called !!!");

        //Use Set to restrict duplicate field names globally
        Set<String> uniqueFieldNames = new HashSet<>();
        List<ComponentField> uniqueFields = new ArrayList<>();

        // Loop through all incoming fields
        for (ComponentField field : request.getFields()) {
            String fieldName = field.getFieldName();

            // Basic null/empty check
            if (fieldName == null || fieldName.trim().isEmpty()) {
                log.info("Skipping unnamed field (empty name)");
                continue;
            }

            // Duplicate check
            if (uniqueFieldNames.add(fieldName)) {
                uniqueFields.add(field); // add only unique ones
            } else {
                log.info("️Duplicate field '{}' found — skipping this field.", fieldName);
            }
        }

        // Update request with only unique fields
        request.setFields(uniqueFields);

        // Proceed with dialog, Sling Model, and HTL updates
        updateDialog(projectName, request);
        updateSlingModel(request);
        updateHTL(projectName, request, packageName);

        log.info("FILEGEN: Component '{}' updated successfully ({} unique fields)",
                request.getComponentName(), uniqueFields.size());
    }

    // -------------------- update content.xml --------------------

    public static void updateContentXml(String projectName, ComponentRequest request) throws IOException {
        // Path to the actual component .content.xml
        log.info("FILEGEN: updateContentXml method called : " );
        File contentXml = new File(PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components/" + request.getComponentName() + "/.content.xml");
        log.info("updateContentXml contentxml folder : "+ contentXml);
        if (!contentXml.exists()) return;

        String xmlContent = FileUtils.readFileToString(contentXml, StandardCharsets.UTF_8);

        // Update jcr:title if providedpatchSlingModel
        if (request.getSectionTitle() != null) {
            xmlContent = xmlContent.replaceAll("jcr:title=\"[^\"]*\"", "jcr:title=\"" + request.getSectionTitle() + "\"");
        }

        // Update component group if provided
        if (request.getComponentGroup() != null) {
            if (xmlContent.contains("componentGroup=")) {
                xmlContent = xmlContent.replaceAll("componentGroup=\"[^\"]*\"", "componentGroup=\"" + request.getComponentGroup() + "\"");
            } else {
                // If attribute not present, insert it after jcr:primaryType
                xmlContent = xmlContent.replaceFirst("jcr:primaryType=\"[^\"]*\"",
                        "$0 componentGroup=\"" + request.getComponentGroup() + "\"");
            }
        }

        FileUtils.writeStringToFile(contentXml, xmlContent, StandardCharsets.UTF_8);
    }


    // -------------------- update Dialog content.xml --------------------

    public static void updateDialog(String projectName, ComponentRequest request) throws Exception {
        log.info("FILEGEN: updateDialog method called !!!");

        String dialogPath = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components/" + request.getComponentName() + "/_cq_dialog/.content.xml";

        File dialogFile = new File(dialogPath);
        if (!dialogFile.exists()) {
            throw new FileNotFoundException("Dialog file not found at: " + dialogPath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(dialogFile);
        doc.getDocumentElement().normalize();

        // Determine presence of tabs
        boolean hasTabs = request.getFields().stream()
                .anyMatch(f -> "tabs".equalsIgnoreCase(f.getFieldType()));

        // Ensure <content> exists
        Element content = findChildByName(doc.getDocumentElement(), "./content");
        if (content == null) {
            content = doc.createElement("content");
            content.setAttribute("jcr:primaryType", "nt:unstructured");
            doc.getDocumentElement().appendChild(content);
        }

        // Ensure <layout> exists and set correct layout type
        Element layout = findChildByName(content, "./layout");
        if (layout == null) {
            layout = doc.createElement("layout");
            layout.setAttribute("jcr:primaryType", "nt:unstructured");
            content.appendChild(layout);
        }
        if (hasTabs) {
            layout.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/layouts/tabs");
        } else {
            layout.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/layouts/fixedcolumns");
        }

        // Ensure <items> under <content>
        Element itemsElement = ensureChild(doc, content, "items", null);

        // Remove old tab/column remnants that conflict with desired structure
        NodeList children = itemsElement.getChildNodes();
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String name = node.getNodeName();
                // remove tabs/tb* when switching to fixedcolumns and remove columns when switching to tabs
                if (hasTabs) {
                    if (name.equalsIgnoreCase("column") || name.startsWith("tb")) {
                        toRemove.add((Element) node);
                    }
                } else {
                    if (name.equalsIgnoreCase("tabs") || name.startsWith("tb") || name.equalsIgnoreCase("main")) {
                        toRemove.add((Element) node);
                    }
                }
            }
        }
        for (Element e : toRemove) {
            itemsElement.removeChild(e);
        }

        // Cleanup deleted fields (top-level + nested)
        List<String> incomingNames = request.getFields().stream()
                .map(ComponentField::getFieldName)
                .collect(Collectors.toList());
        cleanUpDeletedFields(itemsElement, incomingNames);
        log.info("[updateDialog method] - incomingNames : " + incomingNames );

        // Separate tab fields and non-tab fields
        List<ComponentField> tabFields = new ArrayList<>();
        List<ComponentField> nonTabFields = new ArrayList<>();
        for (ComponentField f : request.getFields()) {
            if ("tabs".equalsIgnoreCase(f.getFieldType())) {
                tabFields.add(f);
            } else {
                nonTabFields.add(f);
            }
        }
        //Filter out empty/deleted multifields
        nonTabFields = nonTabFields.stream()
                .filter(f -> !("multifield".equalsIgnoreCase(f.getFieldType()) &&
                        (f.getNestedFields() == null || f.getNestedFields().isEmpty())))
                .collect(Collectors.toList());

        // Branch: TAB mode vs FIXEDCOLUMNS mode
        if (hasTabs) {
            // Ensure <tabs> container
            Element tabsNode = findChildByName(itemsElement, "./tabs");
            if (tabsNode == null) {
                tabsNode = doc.createElement("tabs");
                tabsNode.setAttribute("jcr:primaryType", "nt:unstructured");
                tabsNode.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/tabs");
                itemsElement.appendChild(tabsNode);
            }
            Element tabsItems = ensureChild(doc, tabsNode, "items", null);

            // Create each defined tab (tb1, tb2, etc.) and populate
            for (ComponentField tabField : tabFields) {
                Element tabNode = ensureChild(doc, tabsItems, tabField.getFieldName(), null);
                tabNode.setAttribute("jcr:primaryType", "nt:unstructured");
                tabNode.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/container");
                tabNode.setAttribute("jcr:title", tabField.getFieldLabel());

                Element tabItems = ensureChild(doc, tabNode, "items", null);
                handleNestedFields(doc, tabItems, tabField.getNestedFields());
            }

            // Non-tab fields go under Main tab (only when tabs exist)
            if (!nonTabFields.isEmpty()) {
                Element mainTab = findChildByName(tabsItems, "./main");
                if (mainTab == null) {
                    mainTab = doc.createElement("main");
                    mainTab.setAttribute("jcr:primaryType", "nt:unstructured");
                    mainTab.setAttribute("jcr:title", "Main");
                    mainTab.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/container");
                    tabsItems.appendChild(mainTab);
                }
                Element mainItems = ensureChild(doc, mainTab, "items", null);

                for (ComponentField f : nonTabFields) {
                    Element existing = findChildByName(itemsElement, "./" + f.getFieldName());
                    if (existing != null) {
                        // move existing top-level node into mainItems
                        itemsElement.removeChild(existing);
                        mainItems.appendChild(existing);
                        updateFieldNode(doc, mainItems, f);
                    } else {
                        Element newNode = updateFieldNode(doc, mainItems, f);
                        insertAtCorrectPosition(mainItems, newNode, nonTabFields, f.getFieldName());
                    }
                }
            }

        } else {
            // FIXEDCOLUMNS (no tabs) -> content -> layout(fixedcolumns) -> items -> column -> items -> fields

            // Ensure column exists directly under itemsElement
            Element column = findChildByName(itemsElement, "./column");
            if (column == null) {
                column = doc.createElement("column");
                column.setAttribute("jcr:primaryType", "nt:unstructured");
                column.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/container");
                itemsElement.appendChild(column);
            }
            Element columnItems = ensureChild(doc, column, "items", null);

            // Place all non-tab fields under columnItems (preserve order)
            for (ComponentField f : nonTabFields) {
                Element existing = findChildByName(itemsElement, "./" + f.getFieldName());
                if (existing != null) {
                    // move existing top-level node into columnItems
                    itemsElement.removeChild(existing);
                    columnItems.appendChild(existing);
                    updateFieldNode(doc, columnItems, f);
                } else {
                    Element newNode = updateFieldNode(doc, columnItems, f);
                    insertAtCorrectPosition(columnItems, newNode, nonTabFields, f.getFieldName());
                }
            }
        }

        // Write formatted XML back to file
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        String formattedXml = XmlUtil.formatXml(writer.toString());

        try (FileWriter fw = new FileWriter(dialogFile)) {
            fw.write(formattedXml);
        }

        log.info("FILEGEN: Dialog .content.xml updated and formatted at {}", dialogFile.getAbsolutePath());
    }

    /**
     * Inserts a field node into itemsElement preserving order from request.getFields()
     */
    private static void insertAtCorrectPosition(Element itemsElement,
                                                Element newField,
                                                List<ComponentField> fields,
                                                String fieldName) {
        NodeList childNodes = itemsElement.getChildNodes();

        // find index of this field in request order
        int desiredIndex = -1;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getFieldName().equals(fieldName)) {
                desiredIndex = i;
                break;
            }
        }

        if (desiredIndex == -1) {
            itemsElement.appendChild(newField); // fallback
            return;
        }

        // find sibling to insert before
        int currentIndex = 0;
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node n = childNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;

            String existingName = ((Element) n).getAttribute("name").replace("./", "");
            int existingFieldIndex = -1;
            for (int j = 0; j < fields.size(); j++) {
                if (fields.get(j).getFieldName().equals(existingName)) {
                    existingFieldIndex = j;
                    break;
                }
            }

            if (existingFieldIndex > desiredIndex) {
                itemsElement.insertBefore(newField, n); // insert before next higher index
                return;
            }
            currentIndex++;
        }

        // if no higher index found, append at end
        itemsElement.appendChild(newField);
    }


    private static Element updateFieldNode(Document doc, Element parent, ComponentField field) {
        String type = field.getFieldType().toLowerCase();
        String fieldName = field.getFieldName();
        String resourceType = FieldType.getTypeResourceMap()
                .getOrDefault(type, "granite/ui/components/coral/foundation/form/textfield");

        Element node = findChildByName(parent, "./" + fieldName);
        if (node == null) {
            node = doc.createElement(fieldName);
            node.setAttribute("jcr:primaryType", "nt:unstructured");
            //node.setAttribute("name", "./" + fieldName);
            if (!"multifield".equals(type)) {      // <-- skip name for multifields
                node.setAttribute("name", "./" + fieldName);
            }
           // parent.appendChild(node);
        }

        node.setAttribute("fieldLabel", field.getFieldLabel());
        node.setAttribute("sling:resourceType", resourceType);

        switch (type) {
            case "textarea":
                node.setAttribute("rows", "5");
                break;

            case "numberfield":
                node.setAttribute("min", "0");
                node.setAttribute("max", "1000");
                break;

            case "colorfield":
                node.setAttribute("emptyText", "Choose a color");
                node.setAttribute("value", "#ffffff");
                node.setAttribute("required", "{Boolean}false");
                break;

            case "tabs":
                node.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/tabs");
                node.setAttribute("jcr:primaryType", "nt:unstructured");

                // ensure <items> inside tabs
                Element tabItems = ensureChild(doc, node, "items", null);

                // for each nested tab, create container
                if (field.getNestedFields() != null) {
                    for (ComponentField tab : field.getNestedFields()) {
                        Element tabNode = findChildByName(tabItems, "./" + tab.getFieldName());
                        if (tabNode == null) {
                            tabNode = doc.createElement(tab.getFieldName());
                            tabNode.setAttribute("jcr:primaryType", "nt:unstructured");
                            tabNode.setAttribute("jcr:title", tab.getFieldLabel());
                            tabNode.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/container");
                            tabItems.appendChild(tabNode);
                        }
                        // ensure <items> inside each tab
                        Element tabInnerItems = ensureChild(doc, tabNode, "items", null);

                        //handle only normal & multifield fields
                        handleNestedFields(doc, tabInnerItems, tab.getNestedFields());
                    }
                }
                break;

            case "richtext":
                node.setAttribute("sling:resourceType", "cq/gui/components/authoring/dialog/richtext");
                node.setAttribute("useFixedInlineToolbar", "true");
                node.setAttribute("enableSourceEdit", "true");
                break;

            case "checkbox":
                node.setAttribute("text", field.getFieldLabel());
                node.setAttribute("value", "true");
                node.setAttribute("uncheckedValue", "false");
                break;

            case "tagfield":
                node.setAttribute("autocompleter", "true");
                node.setAttribute("multiple", "true");
                node.setAttribute("rootPath", "/content/cq:tags");
                break;

            case "fileupload":
            case "image":
                node.setAttribute("sling:resourceType", "cq/gui/components/authoring/dialog/fileupload");
                node.setAttribute("autoStart", "{Boolean}false");
                node.setAttribute("class", "cq-droptarget");
                node.setAttribute("fileNameParameter", "./" + fieldName + "FileName");
                node.setAttribute("fileReferenceParameter", "./" + fieldName);
                node.setAttribute("mimeTypes", "[image/gif,image/jpeg,image/png,image/tiff,image/svg+xml]");
                node.setAttribute("multiple", "{Boolean}false");
                node.setAttribute("name", "./file");
                node.setAttribute("uploadUrl", "/content/dam");
                break;

            case "select":
            case "multiselect":
            case "radiogroup":
                Element items = ensureChild(doc, node, "items", null);
                while (items.hasChildNodes()) {
                    items.removeChild(items.getFirstChild());
                }
                int i = 1;
                for (OptionItem opt : field.getOptions()) {
                    Element optEl = doc.createElement("option" + i++);
                    optEl.setAttribute("jcr:primaryType", "nt:unstructured");
                    optEl.setAttribute("text", opt.getText());
                    optEl.setAttribute("value", opt.getValue());
                    items.appendChild(optEl);
                }
                if ("multiselect".equals(type)) {
                    node.setAttribute("multiple", "{Boolean}true");
                }
                if ("select".equals(type) || "multiselect".equals(type)) {
                    node.setAttribute("emptyText", "Select...");
                }
                break;

            case "multifield":
                node.setAttribute("composite", "true");
                node.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/form/multifield");

                Element fieldset = ensureChild(doc, node, "field", "./" + field.getFieldName());
                fieldset.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/form/fieldset");

                Element layout = ensureChild(doc, fieldset, "layout", null);
                layout.setAttribute("jcr:primaryType", "nt:unstructured");
                layout.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/layouts/fixedcolumns");
                layout.setAttribute("margin", "true");

                Element mfItems = ensureChild(doc, fieldset, "items", null);

                // Directly create fields only once — no nested multifield handling
                if (field.getNestedFields() != null && !field.getNestedFields().isEmpty()) {
                    for (ComponentField inner : field.getNestedFields()) {
                        updateFieldNode(doc, mfItems, inner);
                    }
                }

                // ensure multifield node is appended
                if (node.getParentNode() == null) {
                    parent.appendChild(node);
                }
                break;

        }

        if (!"tabs".equals(type) && !"multifield".equals(type)) {
            // Normal fields go directly under parent items
            if (!parent.hasChildNodes() || findChildByName(parent, "./" + fieldName) == null) {
                parent.appendChild(node);
            }
        }

        return node;

    }

    private static void handleNestedFields(Document doc, Element parentItems, List<ComponentField> nestedFields) {
        if (nestedFields == null || nestedFields.isEmpty()) return;

        for (ComponentField nested : nestedFields) {
            String fieldType = nested.getFieldType().toLowerCase();

            // Handle only normal + multifield + tabs directly (no recursion)
            updateFieldNode(doc, parentItems, nested);
        }
    }

    private static Element findChildByName(Element parent, String name) {
        String clean = name.startsWith("./") ? name.substring(2) : name;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;

            String nameAttr = el.getAttribute("name");
            String fileRef = el.getAttribute("fileReferenceParameter");

            boolean matchByName = ("./" + clean).equals(nameAttr);
            boolean matchFileUpload = ("./" + clean).equals(fileRef)
                    || ("./file".equals(nameAttr) && ("./" + clean).equals(fileRef));
            boolean matchByNodeName = el.getNodeName().equalsIgnoreCase(clean);

            if (matchByName || matchFileUpload || matchByNodeName) {
                return el;
            }
        }
        return null;
    }


    /**
     * Utility: ensure a child exists, else create it
     */
    private static Element ensureChild(Document doc, Element parent, String nodeName, String nameAttr) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) n;
                if (el.getNodeName().equals(nodeName)) {
                    return el; // return direct child
                }
            }
        }

        Element child = doc.createElement(nodeName);
        child.setAttribute("jcr:primaryType", "nt:unstructured");
        if (nameAttr != null) {
            child.setAttribute("name", nameAttr);
        }
        parent.appendChild(child);
        return child;
    }


    // Recursive method to clean up fields (top-level + nested)
    private static void cleanUpDeletedFields(Element itemsElement, List<String> incomingNames) {
        log.info("cleanUpDeletedFields method called for deleting the fields....!!");
        log.info("cleanUpDeletedFields fields : " + incomingNames);
        NodeList childNodes = itemsElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element el = (Element) node;
            String nameAttr = el.getAttribute("name");
            String fileRefAttr = el.getAttribute("fileReferenceParameter");
            String nodeName = el.getNodeName();

            boolean shouldRemove = false;

            // Case 1: match by "name" attribute
            if (nameAttr != null && !nameAttr.isEmpty()) {
                if (!incomingNames.contains(nameAttr.replace("./", ""))) {
                    shouldRemove = true;
                }
            }
            // Case 2: match by "fileReferenceParameter"
            else if (fileRefAttr != null && !fileRefAttr.isEmpty()) {
                if (!incomingNames.contains(fileRefAttr.replace("./", ""))) {
                    shouldRemove = true;
                }
            }
            // 🆕 Case 3: match by element nodeName itself (for multifields like multitabs)
            else if (!incomingNames.contains(nodeName)) {
                shouldRemove = true;
            }

            if (shouldRemove) {
                itemsElement.removeChild(el);
                i--; // adjust loop since NodeList is live
                continue;
            }

            // Recursive cleanup for nested <items>
            NodeList nestedItems = el.getElementsByTagName("items");
            for (int j = 0; j < nestedItems.getLength(); j++) {
                Element nested = (Element) nestedItems.item(j);
                cleanUpDeletedFields(nested, incomingNames);
            }
        }
    }

    // -------------------- update Sling model --------------------

    public static void updateSlingModel(ComponentRequest request) throws IOException {
        log.info("FILEGEN: updateSlingModel method called !!! " );
        // 1. Derive HTL path
        Path htlPath = Paths.get("generated-projects", request.getProjectName(),
                "ui.apps/src/main/content/jcr_root/apps",
                request.getProjectName(), "components", request.getComponentName(),
                request.getComponentName() + ".html");

        // 2. Extract Sling Model class name from HTL
        String htlContent = Files.readString(htlPath);
        String slingModelClass = extractSlingModelClass(htlContent);
        if (slingModelClass == null) {
            throw new RuntimeException("No Sling Model found in HTL: " + htlPath);
        }

        // 3. Locate Sling Model .java file
        Path javaFilePath = locateJavaFile(request.getProjectName(), slingModelClass);
        if (javaFilePath == null) {
            throw new RuntimeException("Could not find Java file for model: " + slingModelClass);
        }
        log.info("updateSlingModel javaFilePath ...!!" + javaFilePath);

        // 4. Determine base package for generating nested multifield classes
        String basePackage = slingModelClass.substring(0, slingModelClass.lastIndexOf("."));
        log.info("updateSlingModel basePackage ...!!" + basePackage);

        // 5. Patch Sling Model with fields from ComponentRequest
        patchSlingModel(javaFilePath, request.getFields(), basePackage, request.getProjectName());
    }


    public static String extractSlingModelClass(String htlContent) {
        Pattern p = Pattern.compile("data-sly-use\\.\\w+\\s*=\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(htlContent);
        while (m.find()) {
            String candidate = m.group(1).trim();
            // Ignore templates and only pick Java classes
            if (candidate.contains(".") && !candidate.endsWith(".html")) {
                return candidate;
            }
        }
        return null;
    }


    private static Path locateJavaFile(String projectName, String slingModelClass) {
        String relativePath = slingModelClass.replace(".", "/") + ".java";
        Path javaPath = Paths.get("generated-projects", projectName,
                "core/src/main/java", relativePath);
        return Files.exists(javaPath) ? javaPath : null;
    }


    /**
     * Patch-update Sling Model file based on ComponentRequest fields.
     */
    private static void patchSlingModel(Path javaFile, List<ComponentField> fields,
                                        String basePackage, String projectName) throws IOException {

        log.info("patchSlingModel Method called....!!");

        String content = Files.readString(javaFile);
        log.info("patchSlingModel content ...!!" + content);

        // 1️⃣ Extract existing fields
        Map<String, String> existingFields = extractFieldMap(content);
        log.info("patchSlingModel existingFields ...!!" + existingFields);

        // 2️⃣ Remove fields that no longer exist
        for (String fieldName : new HashSet<>(existingFields.keySet())) {
            if (fields.stream().noneMatch(f -> f.getFieldName().equals(fieldName))) {
                content = removeField(content, fieldName);
                // 2️⃣ Try deleting nested multifield class (if exists)
                deleteMultifieldClassIfExists(projectName, basePackage, fieldName);
            }
        }

        // 3️⃣ Add or update fields
        for (ComponentField field : fields) {

            // If tab → add nested fields directly to main class
            if ("tabs".equalsIgnoreCase(field.getFieldType()) && field.getNestedFields() != null) {
                for (ComponentField inner : field.getNestedFields()) {
                    if (!fieldExists(content, inner.getFieldName())) {
                        content = insertField(content, inner, basePackage, projectName);
                    } else {
                        content = updateField(content, inner);
                    }

                    // Handle nested multifields in tabs
                    if ("multifield".equalsIgnoreCase(inner.getFieldType()) || "child".equalsIgnoreCase(inner.getFieldType())) {
                        generateOrUpdateMultifieldClass(projectName, basePackage,
                                capitalize(inner.getFieldName()), inner.getNestedFields());
                    }
                }
                continue; // skip creating a field for the tab itself
            }

            // Regular field
            if (fieldExists(content, field.getFieldName())) {
                content = updateField(content, field); // update type if changed
            } else {
                content = insertField(content, field, basePackage, projectName);
            }

            // 4️⃣ Generate nested classes for multifield or child elements
            if ("multifield".equalsIgnoreCase(field.getFieldType()) ||
                    "child".equalsIgnoreCase(field.getFieldType())) {

                String nestedClassName = capitalize(field.getFieldName());
                generateOrUpdateMultifieldClass(projectName, basePackage, nestedClassName, field.getNestedFields());
            }
        }

        // 5️⃣ Rebuild isEmpty()
        content = updateIsEmpty(content, fields);

        Files.writeString(javaFile, content);
        // 🧹 Format the updated Sling Model file
        cleanAndFormatJavaFile(javaFile.toFile());
        log.info("Formatted Sling Model: {}", javaFile.getFileName());

        try {
            // ✅ 1. Define correct test base path
            String testBasePath = Paths.get("generated-projects", projectName,
                    "core/src/test/java").toString();

            // ✅ 2. Get model class name (e.g. CrazyModel)
            String className = javaFile.getFileName().toString().replace(".java", "");

            // ✅ 3. Build proper modelBasePath for tests (includes package)
            String modelBasePathForTests = Paths.get(testBasePath, basePackage.replace(".", "/")).toString();

            // ✅ Ensure the package directory exists
            Files.createDirectories(Paths.get(modelBasePathForTests));

            // ✅ Generate or update JUnit test in correct folder
            generateJUnitTestForModel(modelBasePathForTests, basePackage, className, fields);
            log.info("✅ JUnit generated/updated for model: {} at {}", className, modelBasePathForTests);

        } catch (Exception e) {
            log.warn("⚠️ Failed to generate or update JUnit for model {}: {}", javaFile.getFileName(), e.getMessage());
        }


    }
    
    /**
     * Deletes the corresponding multifield nested Sling Model class if it exists.
     */
    private static void deleteMultifieldClassIfExists(String projectName, String basePackage, String fieldName) {
        try {
            String nestedClassName = capitalize(fieldName);
            Path modelDir = Paths.get("generated-projects", projectName,
                    "core/src/main/java", basePackage.replace(".", "/"));
            Path nestedClassPath = modelDir.resolve(nestedClassName + ".java");

            if (Files.exists(nestedClassPath)) {
                Files.delete(nestedClassPath);
                log.info("Deleted nested multifield Sling Model class: {}", nestedClassPath);
            } else {
                log.info("No nested Sling Model found for multifield '{}'", fieldName);
            }

            // --- Delete corresponding JUnit test class ---
            Path testDir = Paths.get("generated-projects", projectName,
                    "core/src/test/java", basePackage.replace(".", "/"));
            Path testClassPath = testDir.resolve(nestedClassName + "Test.java");

            if (Files.exists(testClassPath)) {
                Files.delete(testClassPath);
                log.info("Deleted corresponding JUnit test class: {}", testClassPath);
            } else {
                log.info("No JUnit test found for multifield '{}'", fieldName);
            }

        } catch (Exception e) {
            log.warn("Failed to delete nested Sling Model for field '{}': {}", fieldName, e.getMessage());
        }
    }

    private static String insertField(String content, ComponentField field,
                                      String packageName, String projectName) {

        String capName = capitalize(field.getFieldName());
        String fieldCode;

        // Handle multifield or child
        if ("multifield".equalsIgnoreCase(field.getFieldType()) ||
                "child".equalsIgnoreCase(field.getFieldType())) {

            String nestedClassName = capName;
            fieldCode =
                    "    @ChildResource\n" +
                            "    private List<" + nestedClassName + "> " + field.getFieldName() + ";\n\n" +
                            "    public List<" + nestedClassName + "> get" + nestedClassName + "() {\n" +
                            "        return " + field.getFieldName() + ";\n" +
                            "    }\n\n";
        } else {
            String type = mapFieldTypeToJavaType(field.getFieldType());
            fieldCode =
                    "    @ValueMapValue\n" +
                            "    private " + type + " " + field.getFieldName() + ";\n\n" +
                            "    public " + type + " get" + capName + "() {\n" +
                            "        return " + field.getFieldName() + ";\n" +
                            "    }\n\n";
        }

        // 🔍 Find where to insert (before isEmpty() or its comment)
        Pattern commentPattern = Pattern.compile("/\\*\\*\\s*\\*\\s*Checks if all fields.*?\\*/", Pattern.DOTALL);
        Matcher matcher = commentPattern.matcher(content);

        int insertPos;
        if (matcher.find()) {
            insertPos = matcher.start(); // Insert before the comment
        } else {
            // fallback if comment not found
            insertPos = content.lastIndexOf("}");
        }

        // Insert field code before isEmpty() comment/method
        return content.substring(0, insertPos) + fieldCode + content.substring(insertPos);
    }

    private static boolean fieldExists(String content, String fieldName) {
        Pattern pField = Pattern.compile("private\\s+[\\w<>\\[\\]]+\\s+" + fieldName + "\\s*;");
        Pattern pGetter = Pattern.compile("public\\s+[\\w<>\\[\\]]+\\s+get" + capitalize(fieldName) + "\\s*\\(");
        return pField.matcher(content).find() || pGetter.matcher(content).find();
    }

    // Extract all @ValueMapValue / @ChildResource fields reliably
    private static Map<String, String> extractFieldMap(String content) {
        Map<String, String> map = new HashMap<>();
        Pattern p = Pattern.compile("@(?:ValueMapValue|ChildResource)\\s+private\\s+([\\w<>]+)\\s+(\\w+)\\s*;");
        Matcher m = p.matcher(content);
        while (m.find()) {
            map.put(m.group(2), m.group(1)); // fieldName → type
        }
        return map;
    }

    // Remove a field and its getter
    private static String removeField(String content, String fieldName) {

        log.info("removeField method called....!!" + fieldName );
        // Remove annotated field
        content = content.replaceAll(
                "(?s)@(ValueMapValue|ChildResource)\\s+private[^{;]+\\s+" + fieldName + "\\s*;\\s*",
                ""
        );

        // Remove ALL getters for this field
        content = content.replaceAll(
                "(?s)public\\s+[\\w<>\\[\\]]+\\s+(get|is)" + capitalize(fieldName) +
                        "\\s*\\(\\)\\s*\\{.*?\\}",
                ""
        );

        // Clean up any excessive blank lines left
        content = content.replaceAll("(?m)(\\n\\s*){3,}", "\n\n");

        return content.trim() + "\n";
    }

    // Update existing field type/annotation if needed
    private static String updateField(String content, ComponentField field) {
        String fieldName = field.getFieldName();
        String capName = capitalize(fieldName);
        String type;
        String annotation;

        if ("multifield".equalsIgnoreCase(field.getFieldType()) ||
                "child".equalsIgnoreCase(field.getFieldType())) {
            type = "List<" + capName + ">";
            annotation = "@ChildResource";
        } else {
            type = mapFieldTypeToJavaType(field.getFieldType());
            annotation = "@ValueMapValue";
        }

        // Replace field declaration
        content = content.replaceAll(
                "(?s)@(?:ValueMapValue|ChildResource)\\s+private[^{;]+\\s+" + fieldName + "\\s*;",
                annotation + "\n private " + type + " " + fieldName + ";"
        );

        // Replace getter return type
        content = content.replaceAll(
                "(?s)public\\s+[\\w<>\\[\\]]+\\s+(get|is)" + capName + "\\s*\\(",
                "public " + type + " get" + capName + "("
        );

        return content;
    }


    /**
     * Recursive generator for multifield classes
     */
    private static void generateOrUpdateMultifieldClass(String projectName, String packageName,
                                                        String className, List<ComponentField> nestedFields)
            throws IOException {

        String relativePath = packageName.replace(".", "/") + "/" + className + ".java";
        Path javaFile = Paths.get("generated-projects", projectName, "core/src/main/java", relativePath);
        Files.createDirectories(javaFile.getParent());

        // Create skeleton if missing
        if (!Files.exists(javaFile)) {
            String header = "package " + packageName + ";\n\n" +
                    "import java.util.List;\n" +
                    "import org.apache.sling.api.resource.Resource;\n" +
                    "import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n" +
                    "import org.apache.sling.models.annotations.Model;\n" +
                    "import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;\n" +
                    "import org.apache.sling.models.annotations.injectorspecific.ChildResource;\n\n" +
                    "@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)\n" +
                    "public class " + className + " {\n\n}\n";
            Files.writeString(javaFile, header);
        }

        String content = Files.readString(javaFile);

        Map<String, String> existing = extractFieldMap(content);

        // Remove old fields
        for (String f : new HashSet<>(existing.keySet())) {
            if (nestedFields.stream().noneMatch(n -> n.getFieldName().equals(f))) {
                content = removeField(content, f);
            }
        }

        // Insert or update nested fields
        for (ComponentField nested : nestedFields) {
            if (fieldExists(content, nested.getFieldName())) {
                content = updateField(content, nested);
            } else {
                content = insertField(content, nested, packageName, projectName);

                // Recursively generate further nested multifields
                /*if ("multifield".equalsIgnoreCase(nested.getFieldType()) ||
                        "child".equalsIgnoreCase(nested.getFieldType())) {

                    generateOrUpdateMultifieldClass(projectName, packageName,
                            capitalize(nested.getFieldName()), nested.getNestedFields());
                }*/
            }
        }

        // Rebuild isEmpty
        content = updateIsEmpty(content, nestedFields);

        Files.writeString(javaFile, content);
        cleanAndFormatJavaFile(javaFile.toFile());
        log.info("Formatted nested multifield Sling Model: {}", javaFile.getFileName());

        // ---- Generate or Update corresponding JUnit Test class ----
        try {
            String testBasePath = Paths.get("generated-projects", projectName,
                    "core/src/test/java").toString();

            String modelBasePath = Paths.get("generated-projects", projectName,
                    "core/src/main/java", packageName.replace(".", "/")).toString();

            // ✅ Always generate/update JUnit (even if it already exists)
            generateJUnitTestForModel(modelBasePath, packageName, className, nestedFields);

            log.info("✅ JUnit generated/updated for multifield model: {}", className);
        } catch (Exception e) {
            log.warn("⚠️ Failed to generate/update JUnit for multifield model {}: {}", className, e.getMessage());
        }

    }

    private static String updateIsEmpty(String content, List<ComponentField> fields) {
        // Remove existing isEmpty method
        content = content.replaceAll("(?s)public\\s+boolean\\s+isEmpty\\s*\\(\\)\\s*\\{.*?\\}", "");

        StringBuilder checks = new StringBuilder();

        for (ComponentField f : fields) {
            String fieldName = f.getFieldName();
            String fieldType = f.getFieldType().toLowerCase();

            switch (fieldType) {
                case "numberfield":
                    checks.append("        if (").append(fieldName).append(" != 0) empty = false;\n");
                    break;

                case "checkbox":
                    checks.append("        if (").append(fieldName).append(") empty = false;\n");
                    break;

                case "tabs":
                    // Add checks for each nested field inside the tab
                    if (f.getNestedFields() != null) {
                        for (ComponentField inner : f.getNestedFields()) {
                            String innerName = inner.getFieldName();
                            String innerType = inner.getFieldType().toLowerCase();

                            switch (innerType) {
                                case "numberfield":
                                    checks.append("        if (").append(innerName).append(" != 0) empty = false;\n");
                                    break;
                                case "checkbox":
                                    checks.append("        if (").append(innerName).append(") empty = false;\n");
                                    break;
                                case "multifield":
                                case "child":
                                    checks.append("        if (").append(innerName)
                                            .append(" != null && !").append(innerName).append(".isEmpty()) empty = false;\n");
                                    break;
                                default:
                                    checks.append("        if (").append(innerName)
                                            .append(" != null && !").append(innerName).append(".toString().isEmpty()) empty = false;\n");
                            }
                        }
                    }
                    break;

                case "multifield":
                case "child":
                    // Check if list is non-null and not empty
                    checks.append("        if (").append(fieldName)
                            .append(" != null && !").append(fieldName).append(".isEmpty()) empty = false;\n");
                    break;

                default: // String, richtext, etc.
                    checks.append("        if (").append(fieldName)
                            .append(" != null && !").append(fieldName).append(".toString().isEmpty()) empty = false;\n");
            }
        }

        String isEmptyMethod =
                "    public boolean isEmpty() {\n" +
                        "        boolean empty = true;\n" +
                        checks +
                        "        return empty;\n" +
                        "    }\n";

        int insertPos = content.lastIndexOf("}");
        return content.substring(0, insertPos) + isEmptyMethod + "}\n";
    }


    // Utility: map AEM field types to Java types
    private static String mapFieldTypeToJavaType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "numberfield" -> "double";
            case "checkbox" -> "boolean";
            case "textfield", "textarea", "password", "fileupload", "pathfield" -> "String";
            case "tagfield", "multiselect" -> "List<String>";

            default -> "String"; // fallback
        };
    }

    // -------------------- update HTL --------------------

    public static void updateHTL(String projectName, ComponentRequest request, String packageName) throws Exception {
        String htlPath = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components/" + request.getComponentName() + "/" + request.getComponentName() + ".html";

        File htlFile = new File(htlPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config;

        // Load the component-htl.json
        try (InputStream is = FileGenerationUtil.class.getClassLoader().getResourceAsStream("component-htl.json")) {
            if (is == null) throw new FileNotFoundException("component-htl.json not found in resources");
            config = mapper.readTree(is);
        }

        JsonNode fieldTemplates = config.path("fieldTemplates");
        String modelClassName = capitalize(request.getComponentName()) + "Model";

        // If file doesn’t exist → create fresh
        if (!htlFile.exists()) {
            log.warn("HTL file does not exist, creating a new one.");
            generateHTL(htlFile.getParent(), request.getFields(), packageName, request.getComponentName(), null);
            return;
        }

        // Read existing HTL
        String existingHTL = FileUtils.readFileToString(htlFile, StandardCharsets.UTF_8);

        // Identify main content block
        int mainStart = existingHTL.indexOf("<sly data-sly-test.hasContent");
        int mainEnd = existingHTL.lastIndexOf("</sly>");
        if (mainStart < 0 || mainEnd < 0) {
            log.warn("Main content block not found, regenerating HTL...");
            generateHTL(htlFile.getParent(), request.getFields(), packageName, request.getComponentName(), null);
            return;
        }

        // Split HTL into pre, inner, post
        String preHTL = existingHTL.substring(0, mainStart);
        String postHTL = existingHTL.substring(mainEnd);

        // Build inner HTL only using current dialog/Sling model fields
        StringBuilder sbInner = new StringBuilder();
        for (ComponentField field : request.getFields()) {
            StringBuilder fieldMarkup = new StringBuilder();
            appendFieldHTL(fieldMarkup, field, "model", fieldTemplates, packageName, modelClassName, null, "  ", 0);
            sbInner.append(fieldMarkup).append("\n");
        }

        // Merge back the final HTL
        String finalHTL = preHTL
                + "<sly data-sly-test.hasContent=\"${!model.empty}\">\n"
                + sbInner.toString()
                + "</sly>\n"
                + postHTL;

        // Write updated HTL
        FileUtils.writeStringToFile(htlFile, finalHTL, StandardCharsets.UTF_8);
        log.info("HTL updated successfully for component '{}'", request.getComponentName());
    }


}