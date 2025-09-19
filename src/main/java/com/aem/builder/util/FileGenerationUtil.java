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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aem.builder.constants.ComponentConstants.*;
import static com.aem.builder.constants.ModelAttributeKeys.PROJECTS_DIR;
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
                    + "/" + projectName + "/" + CONTENT_ROOT_PATH + superType;
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

}