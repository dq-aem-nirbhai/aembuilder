package com.aem.builder.util;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.model.Enum.FieldType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.nio.file.StandardOpenOption;
import java.util.*;

import java.io.File;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Utility class for generating AEM component files, dialogs, and models.
 */
@Slf4j
public class FileGenerationUtil {

    private static final String PROJECTS_DIR = "generated-projects";

    private static final Logger logger = LoggerFactory.getLogger(FileGenerationUtil.class);

    /**
     * Generates all files required for a component in the given project.
     */
    public static void generateAllFiles(String projectName, ComponentRequest request) {
        logger.info("FILEGEN: Starting file generation for project: {}", projectName);
        try {
            String appsRoot = "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps";
            File appsDir = new File("generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps");

            String appName = projectName;

            File[] dirs = appsDir.listFiles(File::isDirectory);

            if (dirs != null) {
                for (File dir : dirs) {
                    if (!"msm".equals(dir.getName())) {
                        appName = dir.getName(); // Found a valid app folder, store its name
                        break;
                    }
                }
            }

            String basePath = appsRoot + "/" + appName + "/components/";


            Path javaSourceRoot = Paths.get("generated-projects/" + projectName + "/core/src/main/java/");

            // Find models directory
            Path modelPath = findModelBasePath(javaSourceRoot);

            log.info("ModelPath{}", modelPath);


            // Get full model base path
            String modelBasePath = modelPath.toString();

            log.info("ModelBasePath{}", modelBasePath);

            // 5. Convert to Java package name
            String packageName = javaSourceRoot.relativize(modelPath).toString().replace(File.separatorChar, '.');

            log.info("PackageName {}", packageName);

            generateComponent(basePath, modelBasePath, packageName, request.getComponentName(),
                    request.getComponentGroup(), request.getSuperType(), request.getFields());
            logger.info("FILEGEN: Successfully generated all files for project: {}", projectName);
        } catch (Exception e) {
            logger.info("FILEGEN: Error generating files for project: {}", projectName, e);
            e.printStackTrace();
        }
    }

    // Helper method to locate the 'models' directory under src/main/java
    private static Path findModelBasePath(Path javaSourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(javaSourceRoot)) {
            Optional<Path> modelPath = paths
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("models"))
                    .findFirst();

            return modelPath.orElseThrow(() ->
                    new IOException("models directory not found under: " + javaSourceRoot));
        }
    }


    /**
     * Generates component folders, content.xml, HTL, dialog, and Sling model.
     */
    public static void generateComponent(String basePath, String modelBasePath, String packageName,
                                         String componentName, String componentGroup, String superType, List<ComponentField> fields) throws Exception {
        logger.info("COMPONENT: Generating component '{}'", componentName);

        String componentFolder = basePath + "/" + componentName;
        String dialogFolder = componentFolder + "/_cq_dialog";

        new File(dialogFolder).mkdirs();

        boolean extendsComponent = superType != null && !superType.isBlank();
        boolean hasFields = fields != null && !fields.isEmpty();

        generateComponentContentXml(componentFolder, componentName, componentGroup, superType);

        generateHTL(componentFolder, fields, packageName, componentName, superType);

        if (hasFields) {
            generateDialogContentXml(componentName, dialogFolder, superType, fields);
        }

        if (!extendsComponent || hasFields) {
            generateSlingModel(modelBasePath, packageName, componentName, fields);
        }

        logger.info("COMPONENT: Finished generating component '{}'", componentName);
    }

    /**
     * Generates the .content.xml for the component.
     */
    private static void generateComponentContentXml(String folderPath, String componentName, String componentGroup,
                                                    String superType) throws Exception {
        logger.info("CONTENTXML: Generating .content.xml for component '{}'", componentName);
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\"\n" +
                "          xmlns:cq=\"http://www.day.com/jcr/cq/1.0\"\n" +
                "          xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"\n" +
                "          jcr:primaryType=\"cq:Component\"\n" +
                "          jcr:title=\"" + componentName + "\"\n" +
                "          componentGroup=\"" + componentGroup + "\"" +
                (superType != null && !superType.isBlank()
                        ? "\n          sling:resourceSuperType=\"" + superType + "\"" : "") +
                "/>";
        FileUtils.writeStringToFile(new File(folderPath + "/.content.xml"), content, StandardCharsets.UTF_8);
        logger.info("CONTENTXML: .content.xml generated at {}/.content.xml", folderPath);
    }

    /**
     * Generates the HTL (HTML Template Language) file for the component.
     */
    /**
     * Generates the HTL for a component, supporting fields, multifields, and tabs.
     */
    private static void generateHTL(String folderPath, List<ComponentField> fields, String packageName,
                                    String componentName, String superType) throws Exception {
        logger.info("HTL: Generating HTL for component '{}'", componentName);
        String modelClassName = capitalize(componentName) + "Model";
        StringBuilder sb = new StringBuilder();

        boolean extending = superType != null && !superType.isBlank();
        boolean hasFields = fields != null && !fields.isEmpty();

        if (extending && !hasFields) {
            sb.append("<sly data-sly-resource=\"${@ resourceType='")
                    .append(superType).append("'}\"/>\n");
        } else {
            sb.append("<sly data-sly-use.model=\"")
                    .append(packageName).append(".").append(modelClassName).append("\"/>\n");

            sb.append("<sly data-sly-use.placeholderTemplate=\"core/wcm/components/commons/v1/templates.html\"/>\n")
                    .append("<sly data-sly-test.hasContent=\"${!model.empty}\">\n");
            if (extending) {
                sb.append("<sly data-sly-resource=\"${@ resourceType='")
                        .append(superType).append("'}\"/>\n");
            }

            if (hasFields) {
                for (ComponentField field : fields) {
                    String fieldType = field.getFieldType().toLowerCase();

                    // If it's a tab, just go inside its child fields
                    if ("tabs".equals(fieldType)) {
                        logger.info("HTL: Processing tab '{}'", field.getFieldLabel());
                        appendNestedHTL(sb, field.getNestedFields(), "model", "  ");
                        continue;
                    }

                    String fieldName = field.getFieldName();
                    String fieldLabel = field.getFieldLabel();

                    logger.info("HTL: Processing field '{}' of type '{}'", fieldName, fieldType);

                    switch (fieldType) {
                        case "multifield" -> {
                            sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append(" && model.")
                                    .append(fieldName).append(".size > 0}\">\n")
                                    .append("    <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n");
                            appendNestedHTL(sb, field.getNestedFields(), "item", "      ");
                            sb.append("    </ul>\n")
                                    .append("  </sly>\n");
                        }
                        case "checkbox" -> {
                            sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                                    .append("    <p>").append(fieldLabel)
                                    .append(": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n")
                                    .append("  </sly>\n");
                        }
                        case "multiselect", "tagfield" -> {
                            sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append(" && model.")
                                    .append(fieldName).append(".size > 0}\">\n")
                                    .append("    <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n")
                                    .append("      <li>${item}</li>\n")
                                    .append("    </ul>\n")
                                    .append("  </sly>\n");
                        }
                        case "image", "fileupload" -> {
                            sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                                    .append("    <p>").append(fieldLabel)
                                    .append(": <img src=\"${model.").append(fieldName)
                                    .append("}\" alt=\"").append(fieldLabel)
                                    .append("\" style=\"max-width:100%; height:auto;\"/></p>\n")
                                    .append("  </sly>\n");
                        }
                        case "pathfield" -> {
                            sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                                    .append("    <p>").append(fieldLabel).append(": <a href=\"${model.")
                                    .append(fieldName).append("}\">${model.").append(fieldName).append("}</a></p>\n")
                                    .append("  </sly>\n");
                        }
                        case "richtext" -> {
                            sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                                    .append("    <p>").append(fieldLabel).append(": ${model.")
                                    .append(fieldName).append(" @ context='html'}</p>\n")
                                    .append("  </sly>\n");
                        }
                        default -> {
                            sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                                    .append("    <p>").append(fieldLabel).append(": ${model.")
                                    .append(fieldName).append("}</p>\n")
                                    .append("  </sly>\n");
                        }
                    }
                }

                sb.append("</sly>\n");
                sb.append("<sly data-sly-call=\"${placeholderTemplate.placeholder @ isEmpty = !hasContent}\" />\n");
            }
        }

        FileUtils.writeStringToFile(new File(folderPath + "/" + componentName + ".html"), sb.toString(),
                StandardCharsets.UTF_8);
        logger.info("HTL: HTL file generated at {}/{}.html", folderPath, componentName);
    }

    /**
     * Recursively appends HTL markup for nested multifields and tabs.
     */
    private static void appendNestedHTL(StringBuilder sb, List<ComponentField> nestedFields, String modelVar, String indent) {
        if (nestedFields == null || nestedFields.isEmpty()) return;

        for (ComponentField nested : nestedFields) {
            String fieldType = nested.getFieldType().toLowerCase();

            // If it's a tab, skip wrapper and just render children
            if ("tabs".equals(fieldType)) {
                logger.info("HTL: Processing nested tab '{}'", nested.getFieldLabel());
                appendNestedHTL(sb, nested.getNestedFields(), modelVar, indent);
                continue;
            }

            String fieldName = nested.getFieldName();
            String fieldLabel = nested.getFieldLabel();

            sb.append(indent).append("<sly data-sly-test=\"${").append(modelVar).append(".")
                    .append(fieldName).append("}\">\n");

            switch (fieldType) {
                case "image", "fileupload" -> {
                    sb.append(indent).append("  <p>").append(fieldLabel)
                            .append(": <img src=\"${").append(modelVar).append(".").append(fieldName)
                            .append("}\" alt=\"").append(fieldLabel)
                            .append("\" style=\"max-width:100%; height:auto;\"/></p>\n");
                }
                case "richtext" -> sb.append(indent).append("  <p>").append(fieldLabel).append(": ${")
                        .append(modelVar).append(".").append(fieldName).append(" @ context='html'}</p>\n");
                case "pathfield" -> sb.append(indent).append("  <p>").append(fieldLabel).append(": <a href=\"${")
                        .append(modelVar).append(".").append(fieldName).append("}\">${")
                        .append(modelVar).append(".").append(fieldName).append("}</a></p>\n");
                case "checkbox" -> sb.append(indent).append("  <p>").append(fieldLabel)
                        .append(": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n");
                case "multiselect", "tagfield" -> sb.append(indent).append("  <ul data-sly-list.item=\"${")
                        .append(modelVar).append(".").append(fieldName).append("}\">\n")
                        .append(indent).append("    <li>${item}</li>\n")
                        .append(indent).append("  </ul>\n");
                case "multifield" -> {
                    String newVar = modelVar + "." + fieldName;
                    sb.append(indent).append("  <ul data-sly-list.nestedItem=\"${")
                            .append(newVar).append("}\">\n");
                    appendNestedHTL(sb, nested.getNestedFields(), "nestedItem", indent + "    ");
                    sb.append(indent).append("  </ul>\n");
                }
                default -> sb.append(indent).append("  <p>").append(fieldLabel).append(": ${")
                        .append(modelVar).append(".").append(fieldName).append("}</p>\n");
            }

            sb.append(indent).append("</sly>\n");
        }
    }

    /**
     * Generates the dialog .content.xml for the component.
     */
    public static void generateDialogContentXml(String componentName, String dialogFolder,
                                                String superType, List<ComponentField> fields) throws Exception {
        if (fields == null || fields.isEmpty()) {
            logger.info("DIALOG: Skipping dialog generation for component '{}' as no fields defined", componentName);
            return;
        }

        logger.info("DIALOG: Generating dialog .content.xml for component '{}'", componentName);

        String dialogTitle = componentName + " Dialog";
        StringBuilder sb = new StringBuilder();
        String superTypeAttr = (superType != null && !superType.isBlank())
                ? "\n sling:resourceSuperType=\"" + superType + "/cq:dialog\"" : "";

        // Split tabs vs non-tabs
        List<ComponentField> tabFields = new ArrayList<>();
        List<ComponentField> nonTabFields = new ArrayList<>();

        for (ComponentField f : fields) {
            if ("tabs".equalsIgnoreCase(f.getFieldType())) {
                tabFields.add(f);
            } else {
                nonTabFields.add(f);
            }
        }

        // If no tabs at all => flat dialog (better structure with fixedcolumns + column)
        if (tabFields.isEmpty()) {
            sb.append(String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
                              xmlns:cq="http://www.day.com/jcr/cq/1.0"
                              xmlns:jcr="http://www.jcp.org/jcr/1.0"
                              jcr:primaryType="nt:unstructured"
                              jcr:title="%s"
                              sling:resourceType="cq/gui/components/authoring/dialog"%s>
                        <content jcr:primaryType="nt:unstructured"
                                 sling:resourceType="granite/ui/components/coral/foundation/container">
                            <layout jcr:primaryType="nt:unstructured"
                                    sling:resourceType="granite/ui/components/coral/foundation/layouts/fixedcolumns"/>
                            <items jcr:primaryType="nt:unstructured">
                                <column jcr:primaryType="nt:unstructured"
                                        sling:resourceType="granite/ui/components/coral/foundation/container">
                                    <items jcr:primaryType="nt:unstructured">
                    """, dialogTitle, superTypeAttr));

            for (ComponentField f : nonTabFields) {
                sb.append(generateFieldXml(safeNodeName(f.getFieldName(), "field"), f));
            }

            sb.append("""
                                    </items>
                                </column>
                            </items>
                        </content>
                    </jcr:root>
                    """);

        } else {
            // Tabs exist. Try to find an EXPLICIT "Main" tab among them.
            ComponentField explicitMainTab = null;
            for (ComponentField tf : tabFields) {
                String nodeName = safeNodeName(tf.getFieldName(), "tab");
                String title = tf.getFieldLabel();
                if ("main".equalsIgnoreCase(nodeName) ||
                        (title != null && title.trim().equalsIgnoreCase("Main"))) {
                    explicitMainTab = tf;
                    break; // first match wins
                }
            }

            boolean willAutoCreateMain = explicitMainTab == null && !nonTabFields.isEmpty();

            sb.append(String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
                              xmlns:cq="http://www.day.com/jcr/cq/1.0"
                              xmlns:jcr="http://www.jcp.org/jcr/1.0"
                              jcr:primaryType="nt:unstructured"
                              jcr:title="%s"
                              sling:resourceType="cq/gui/components/authoring/dialog"%s>
                        <content jcr:primaryType="nt:unstructured"
                                 sling:resourceType="granite/ui/components/coral/foundation/container">
                            <layout jcr:primaryType="nt:unstructured"
                                    sling:resourceType="granite/ui/components/coral/foundation/layouts/tabs"/>
                            <items jcr:primaryType="nt:unstructured">
                                <tabs jcr:primaryType="nt:unstructured"
                                      sling:resourceType="granite/ui/components/coral/foundation/tabs">
                                    <items jcr:primaryType="nt:unstructured">
                    """, dialogTitle, superTypeAttr));

            // Safety: avoid duplicate tab node names
            java.util.Set<String> writtenTabNodeNames = new java.util.HashSet<>();

            for (ComponentField field : tabFields) {
                String tabNodeName = safeNodeName(field.getFieldName(), "tab");
                String tabTitle = (field.getFieldLabel() != null && !field.getFieldLabel().isBlank())
                        ? field.getFieldLabel() : tabNodeName;
                String resourceType = getResourceType(field.getFieldType());

                if (!writtenTabNodeNames.add(tabNodeName.toLowerCase())) {
                    logger.warn("DIALOG: Duplicate tab node name '{}' detected. Skipping duplicate.", tabNodeName);
                    continue;
                }

                sb.append("        <").append(tabNodeName).append("\n")
                        .append("            jcr:primaryType=\"nt:unstructured\"\n")
                        .append("            jcr:title=\"").append(tabTitle).append("\"\n")
                        .append("            sling:resourceType=\"").append(resourceType).append("\">\n")
                        .append("            <items jcr:primaryType=\"nt:unstructured\">\n");

                if (field.getNestedFields() != null) {
                    for (ComponentField nf : field.getNestedFields()) {
                        sb.append(generateFieldXml(safeNodeName(nf.getFieldName(), "field"), nf));
                    }
                }

                if (explicitMainTab == field && !nonTabFields.isEmpty()) {
                    for (ComponentField f : nonTabFields) {
                        sb.append(generateFieldXml(safeNodeName(f.getFieldName(), "field"), f));
                    }
                }

                sb.append("            </items>\n")
                        .append("        </").append(tabNodeName).append(">\n");
            }

            if (willAutoCreateMain) {
                String resourceType = getResourceType("tabs");
                String autoMainNodeName = "main";
                if (!writtenTabNodeNames.add(autoMainNodeName)) {
                    autoMainNodeName = "main1";
                    writtenTabNodeNames.add(autoMainNodeName);
                }

                sb.append("        <").append(autoMainNodeName).append("\n")
                        .append("            jcr:primaryType=\"nt:unstructured\"\n")
                        .append("            jcr:title=\"Main\"\n")
                        .append("            sling:resourceType=\"").append(resourceType).append("\">\n")
                        .append("            <items jcr:primaryType=\"nt:unstructured\">\n");

                for (ComponentField f : nonTabFields) {
                    sb.append(generateFieldXml(safeNodeName(f.getFieldName(), "field"), f));
                }

                sb.append("            </items>\n")
                        .append("        </").append(autoMainNodeName).append(">\n");
            }

            sb.append("""
                                    </items>
                                </tabs>
                            </items>
                        </content>
                    </jcr:root>
                    """);
        }

        // Write to file
        FileUtils.writeStringToFile(new File(dialogFolder + "/.content.xml"),
                sb.toString(), StandardCharsets.UTF_8);

        logger.info("DIALOG: Dialog .content.xml generated at {}/.content.xml", dialogFolder);
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
     * Generates XML for a single dialog field.
     */
    private static String generateFieldXml(String nodeName, ComponentField field) {
        // No logging here as it's called in a loop and already logged in the caller.
        String type = field.getFieldType().toLowerCase();
        String label = field.getFieldLabel();
        String name = field.getFieldName();
        List<OptionItem> options = field.getOptions();
        List<ComponentField> nested = field.getNestedFields();

        if (nested != null) {
            for (ComponentField nf : nested) {
                if ("tabs".equalsIgnoreCase(nf.getFieldType())) {
                    throw new IllegalArgumentException(
                            "Nested tabs are not allowed inside field: " + name + " (" + label + ")");
                }
            }
        }


        return switch (type) {
            case "textfield", "textarea", "numberfield", "hidden", "password",
                 "pathfield", "datepicker", "tagfield", "richtext", "switch", "colorfield" -> {
                String resource = getResourceType(type);
                String extra = switch (type) {
                    case "textarea" -> "    rows=\"5\"\n";
                    case "numberfield" -> "    min=\"0\" max=\"1000\"\n";
                    case "tagfield" ->
                            "    autocompleter=\"true\"\n    multiple=\"true\"\n    rootPath=\"/content/cq:tags\"\n";
                    case "richtext" -> "    useFixedInlineToolbar=\"true\"\n    enableSourceEdit=\"true\"\n";
                    case "colorfield" ->
                            "    emptyText=\"Choose a color\"\n    value=\"#ffffff\"\n    required=\"{Boolean}false\"\n";
                    default -> "";
                };
                yield String.format(
                        "  <%s\n" +
                                "    jcr:primaryType=\"nt:unstructured\"\n" +
                                "    sling:resourceType=\"%s\"\n" +
                                "    fieldLabel=\"%s\"\n" +
                                "    name=\"./%s\"\n" +
                                extra +
                                "  />\n",
                        nodeName, resource, label, name);
            }


            case "checkbox" -> {
                String resource = getResourceType("checkbox");
                yield String.format(
                        "  <%s\n" +
                                "    jcr:primaryType=\"nt:unstructured\"\n" +
                                "    sling:resourceType=\"%s\"\n" +
                                "    fieldLabel=\"%s\"\n" +
                                "    name=\"./%s\"\n" +
                                "    text=\"%s\"\n" +
                                "    value=\"true\"\n" +
                                "    uncheckedValue=\"false\"\n" +
                                "  />\n",
                        nodeName, resource, label, name, label);
            }

            case "radiogroup" -> {
                String resource = getResourceType("radiogroup");
                StringBuilder sb = new StringBuilder();
                sb.append("  <").append(nodeName).append("\n")
                        .append("    jcr:primaryType=\"nt:unstructured\"\n")
                        .append("    sling:resourceType=\"").append(resource).append("\"\n")
                        .append("    fieldLabel=\"").append(label).append("\"\n")
                        .append("    name=\"./").append(name).append("\">\n")
                        .append("    <items jcr:primaryType=\"nt:unstructured\">\n");
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        OptionItem opt = options.get(i);
                        sb.append("      <option").append(i + 1).append("\n")
                                .append("        jcr:primaryType=\"nt:unstructured\"\n")
                                .append("        text=\"").append(opt.getText()).append("\"\n")
                                .append("        value=\"").append(opt.getValue()).append("\"/>\n");
                    }
                }
                sb.append("    </items>\n")
                        .append("  </").append(nodeName).append(">\n");
                yield sb.toString();
            }

            case "select", "multiselect" -> {
                String resource = getResourceType("select");
                StringBuilder sb = new StringBuilder();
                sb.append("  <").append(nodeName).append("\n")
                        .append("    jcr:primaryType=\"nt:unstructured\"\n")
                        .append("    sling:resourceType=\"").append(resource).append("\"\n")
                        .append("    fieldLabel=\"").append(label).append("\"\n")
                        .append("    name=\"./").append(name).append("\"\n");
                if (type.equals("multiselect")) {
                    sb.append("    multiple=\"{Boolean}true\"\n");
                }
                sb.append("    emptyText=\"Select...\">\n")
                        .append("    <items jcr:primaryType=\"nt:unstructured\">\n");
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        OptionItem opt = options.get(i);
                        sb.append("      <option").append(i + 1).append("\n")
                                .append("        jcr:primaryType=\"nt:unstructured\"\n")
                                .append("        text=\"").append(opt.getText()).append("\"\n")
                                .append("        value=\"").append(opt.getValue()).append("\"/>\n");
                    }
                }
                sb.append("    </items>\n")
                        .append("  </").append(nodeName).append(">\n");
                yield sb.toString();
            }

            case "image", "fileupload" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("  <").append(nodeName).append("\n")
                        .append("    jcr:primaryType=\"nt:unstructured\"\n")
                        .append("    sling:resourceType=\"").append(getResourceType(type)).append("\"\n")
                        .append("    autoStart=\"{Boolean}false\"\n")
                        .append("    class=\"cq-droptarget\"\n")
                        .append("    fieldLabel=\"").append(label).append("\"\n")
                        .append("    fileNameParameter=\"./").append(name).append("FileName\"\n") // this is fine
                        .append("    fileReferenceParameter=\"./").append(name).append("\"\n") // this is what your Sling Model will use
                        .append("    mimeTypes=\"[image/gif,image/jpeg,image/png,image/tiff,image/svg+xml]\"\n")
                        .append("    multiple=\"{Boolean}false\"\n")
                        .append("    name=\"./file\"\n") //  fixed to mimic your example
                        .append("    uploadUrl=\"/content/dam\"/>\n"); //  removed `${request.contextPath}`
                yield sb.toString();
            }


            case "multifield" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("  <").append(nodeName).append("\n")
                        .append("    jcr:primaryType=\"nt:unstructured\"\n")
                        .append("    sling:resourceType=\"").append(getResourceType(type)).append("\"\n")
                        .append("    fieldLabel=\"").append(label).append("\"\n")
                        .append("    composite=\"true\">\n")
                        .append("    <field\n")
                        .append("      jcr:primaryType=\"nt:unstructured\"\n")
                        .append("      sling:resourceType=\"granite/ui/components/coral/foundation/form/fieldset\"\n")
                        .append("      name=\"./").append(name).append("\">\n")
                        .append("        <layout\n")
                        .append("            jcr:primaryType=\"nt:unstructured\"\n")
                        .append("            sling:resourceType=\"granite/ui/components/coral/foundation/layouts/fixedcolumns\"\n")
                        .append("            margin=\"true\"/>\n")
                        .append("        <items jcr:primaryType=\"nt:unstructured\">\n");

                if (nested != null) {
                    for (ComponentField nf : nested) {
                        String subNode = nf.getFieldName().replaceAll("\\W+", "");
                        sb.append(generateFieldXml(subNode, nf));
                    }
                }

                sb.append("        </items>\n")
                        .append("    </field>\n")
                        .append("  </").append(nodeName).append(">\n");
                yield sb.toString();
            }

            default -> {
                System.err.println("Unsupported AEM field type: " + type);
                yield "";
            }
        };
    }

    /**
     * Returns the resource type for a given field type.
     */
    public static String getResourceType(String type) {
        return FieldType.getTypeResourceMap().getOrDefault(type.toLowerCase(), "");
    }


    /**
     * Generates the Sling Model for a component.
     * - Creates the class file
     * - Iterates through fields
     * - Skips "tabs" but goes inside its children
     */
    private static void generateSlingModel(String modelBasePath, String packageName, String componentName,
                                           List<ComponentField> fields) throws Exception {
        logger.info("MODEL: Generating Sling Model for component '{}'", componentName);
        String className = capitalize(componentName) + "Model";
        File modelDir = new File(modelBasePath);
        modelDir.mkdirs();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n")
                .append("import java.util.List;\n")
                .append("import javax.inject.Inject;\n")
                .append("import org.apache.sling.api.resource.Resource;\n")
                .append("import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n")
                .append("import org.apache.sling.models.annotations.Model;\n")
                .append("import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;\n")
                .append("import org.apache.sling.models.annotations.injectorspecific.ChildResource;\n\n")
                .append("@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)\n")
                .append("public class ").append(className).append(" {\n\n");

        List<ComponentField> generatedFields = addFieldsToModel(sb, modelBasePath, packageName, componentName, fields);
        log.info("generateFields   {}", generatedFields);

        sb.append("    /**\n")
                .append("     * Checks if all fields in this model are empty.\n")
                .append("     * Used in HTL: ${!model.empty}\n")
                .append("     */\n")
                .append("    public boolean isEmpty() {\n")
                .append("        boolean empty = true;\n");

        for (ComponentField field : generatedFields) {
            String name = field.getFieldName();
            String type = field.getFieldType().toLowerCase();

            switch (type) {
                case "multifield":
                    sb.append("        if (").append(name).append(" != null && !").append(name).append(".isEmpty()) empty = false;\n");
                    break;
                case "checkbox":
                    sb.append("        if (").append(name).append(") empty = false;\n");
                    break;
                case "multiselect":
                case "tagfield":
                    sb.append("        if (").append(name).append(" != null && !").append(name).append(".isEmpty()) empty = false;\n");
                    break;
                case "numberfield":
                    sb.append("        if (").append(name).append(" != 0) empty = false;\n");
                    break;
                default:
                    sb.append("        if (").append(name).append(" != null && !").append(name).append(".isEmpty()) empty = false;\n");
            }
        }

        sb.append("        return empty;\n")
                .append("    }\n");

        sb.append("}");

        FileUtils.writeStringToFile(new File(modelBasePath + "/" + className + ".java"), sb.toString(),
                StandardCharsets.UTF_8);
        logger.info("MODEL: Sling Model generated at {}/{}.java", modelBasePath, className);
    }

    /**
     * Recursively adds fields to the Sling Model class.
     * - Handles multifield, checkbox, textfield, etc.
     * - Skips "tabs" node but still processes its child fields
     */
    private static List<ComponentField> addFieldsToModel(StringBuilder sb, String modelBasePath, String packageName,
                                                         String componentName, List<ComponentField> fields) throws Exception {

        List<ComponentField> generatedFields = new ArrayList<>();

        if (fields == null)
            return null;

        for (ComponentField field : fields) {
            String name = field.getFieldName();
            String type = field.getFieldType().toLowerCase();

            // Handle "tabs": skip tab node, but process children
            if ("tabs".equals(type)) {
                logger.info("MODEL: Skipping tab '{}', processing its children", name);
                List<ComponentField> nestedFields = addFieldsToModel(sb, modelBasePath, packageName, componentName, field.getNestedFields());
                if (nestedFields != null) generatedFields.addAll(nestedFields);
                continue;
            }

            logger.info("MODEL: Adding field '{}' of type '{}'", name, type);

            switch (type) {
                case "multifield" -> {
                    // Generate a child model class for multifield entries
                    generateChildModelClass(modelBasePath, packageName, componentName, field);
                    sb.append("    @ChildResource\n")
                            .append("    private List<").append(capitalize(name)).append("> ").append(name).append(";\n\n");
                }
                case "checkbox" -> sb.append("    @ValueMapValue\n")
                        .append("   private boolean ").append(name).append(";\n\n");
                case "multiselect", "tagfield" -> sb.append("    @ValueMapValue\n")
                        .append("    private List<String> ").append(name).append(";\n\n");
                case "numberfield" -> sb.append("    @ValueMapValue\n")
                        .append("    private double ").append(name).append(";\n\n");
                default -> sb.append("    @ValueMapValue\n")
                        .append("    private String ").append(name).append(";\n\n");
            }

            // Add getter
            addGetter(sb, field);
            generatedFields.add(field); // this field for isEmpty()

        }

        return generatedFields;
    }

    /**
     * Generates a child model class for multifield entries.
     * - Handles nested fields
     * - Skips "tabs" but processes children
     */
    private static void generateChildModelClass(String modelBasePath, String packageName,
                                                String parentComponentName, ComponentField parentField) throws Exception {
        String className = capitalize(parentField.getFieldName());
        logger.info("MODEL: Generating Child Model for multifield '{}'", parentField.getFieldName());

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n")
                .append("import java.util.List;\n")
                .append("import javax.inject.Inject;\n")
                .append("import org.apache.sling.api.resource.Resource;\n")
                .append("import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n")
                .append("import org.apache.sling.models.annotations.Model;\n")
                .append("import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;\n")
                .append("import org.apache.sling.models.annotations.injectorspecific.ChildResource;\n\n")
                .append("@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)\n")
                .append("public class ").append(className).append(" {\n\n");

        // Add child fields
        addFieldsToModel(sb, modelBasePath, packageName, parentComponentName, parentField.getNestedFields());


        sb.append("}");

        File file = new File(modelBasePath + "/" + className + ".java");
        FileUtils.writeStringToFile(file, sb.toString(), StandardCharsets.UTF_8);
        logger.info("MODEL: Child Model generated at {}/{}.java", modelBasePath, className);
    }

    /**
     * Adds getter method for a field.
     */
    private static void addGetter(StringBuilder sb, ComponentField field) {
        String name = field.getFieldName();
        String getter = capitalize(name);
        String type = field.getFieldType().toLowerCase();

        switch (type) {
            case "multifield" ->
                    sb.append("    public List<").append(getter).append("> get").append(getter).append("() {\n")
                            .append("        return ").append(name).append(";\n    }\n\n");
            case "checkbox" -> sb.append("    public boolean is").append(getter).append("() {\n")
                    .append("        return ").append(name).append(";\n    }\n\n");
            case "multiselect", "tagfield" -> sb.append("    public List<String> get").append(getter).append("() {\n")
                    .append("        return ").append(name).append(";\n    }\n\n");
            case "numberfield" -> sb.append("    public double get").append(getter).append("() {\n")
                    .append("        return ").append(name).append(";\n    }\n\n");
            default -> sb.append("    public String get").append(getter).append("() {\n")
                    .append("        return ").append(name).append(";\n    }\n\n");
        }
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

//-------------------- update files --------------------

    public static void updateAllFiles(String projectName, ComponentRequest request) {
        logger.info("FILEGEN: Starting file update for project: {}", projectName);
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

            logger.info("FILEGEN: Successfully updated all files for project: {}", projectName);
        } catch (Exception e) {
            logger.error("FILEGEN: Error updating files for project: {}", projectName, e);
            e.printStackTrace();
        }
    }


    public static void updateComponent(String projectName, ComponentRequest request, String packageName) throws Exception {
        updateDialog(projectName, request);        // update .content.xml of dialog
        updateSlingModel(request);
        updateHTL(projectName, request, packageName);// update Sling Model (and HTL inside)
    }

    // -------------------- update content.xml --------------------

    public static void updateContentXml(String projectName, ComponentRequest request) throws IOException {
        // Path to the actual component .content.xml
        File contentXml = new File(PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components/" + request.getComponentName() + "/.content.xml");

        if (!contentXml.exists()) return;

        String xmlContent = FileUtils.readFileToString(contentXml, StandardCharsets.UTF_8);

        // Update jcr:title if provided
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

        // Instead of always itemsList.item(0), find the <items> inside <column>
        Element itemsElement = null;
        NodeList columns = doc.getElementsByTagName("column");
        if (columns.getLength() > 0) {
            Element column = (Element) columns.item(0);
            NodeList colItems = column.getElementsByTagName("items");
            if (colItems.getLength() > 0) {
                itemsElement = (Element) colItems.item(0); // ✅ real container for fields
            }
        }

// fallback if no column/items found
        if (itemsElement == null && columns.getLength() > 0) {
            itemsElement = (Element) columns.item(0);
        }


        // ------------------- DELETE step -------------------
        NodeList childNodes = itemsElement.getChildNodes();
        List<String> incomingNames = request.getFields().stream()
                .map(ComponentField::getFieldName)
                .collect(Collectors.toList());
        cleanUpDeletedFields(itemsElement, incomingNames);

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String nameAttr = el.getAttribute("name");
                String fileRefAttr = el.getAttribute("fileReferenceParameter");

                String cleanName = nameAttr.replace("./", "");
                String cleanFileRef = fileRefAttr.replace("./", "");

                if (!incomingNames.contains(cleanName) && !incomingNames.contains(cleanFileRef)) {
                    itemsElement.removeChild(el);  // ✅ remove missing field
                    i--; // adjust loop index
                }
            }
        }

        for (ComponentField field : request.getFields()) {
            Element existing = findChildByName(itemsElement, "./" + field.getFieldName());
            Element newField = updateFieldNode(doc, itemsElement, field);

            if (existing == null) {
                // new field → insert at correct order
                insertAtCorrectPosition(itemsElement, newField, request.getFields(), field.getFieldName());
            } else {
                // existing field → nothing to insert, it's already in place
            }
        }

        // Save back XML
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(dialogFile);
        transformer.transform(source, result);
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

    //==================
    private static Element updateFieldNode(Document doc, Element parent, ComponentField field) {
        String type = field.getFieldType().toLowerCase();
        String fieldName = field.getFieldName();
        String resourceType = FieldType.getTypeResourceMap()
                .getOrDefault(type, "granite/ui/components/coral/foundation/form/textfield");

        Element node = findChildByName(parent, "./" + fieldName);
        if (node == null) {
            node = doc.createElement(fieldName);
            node.setAttribute("jcr:primaryType", "nt:unstructured");
            node.setAttribute("name", "./" + fieldName);

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
                node.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/container");
                node.setAttribute("type", "tabs");
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

                // This is the critical call: properly add nested fields into multifield items
                handleNestedFields(doc, mfItems, field.getNestedFields());
                break;

        }
        return node;

    }


    private static void handleNestedFields(Document doc, Element parentItems, List<ComponentField> nestedFields) {
        for (ComponentField nested : nestedFields) {
            String nestedType = nested.getFieldType().toLowerCase();
            Element nestedNode = findChildByName(parentItems, "./" + nested.getFieldName());
            if (nestedNode == null) {
                nestedNode = doc.createElement(nested.getFieldName());
                nestedNode.setAttribute("jcr:primaryType", "nt:unstructured");
                nestedNode.setAttribute("name", "./" + nested.getFieldName());
                parentItems.appendChild(nestedNode);
            }
            nestedNode.setAttribute("fieldLabel", nested.getFieldLabel());

            if ("multifield".equals(nestedType)) {
                nestedNode.setAttribute("composite", "true");
                nestedNode.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/form/multifield");

                Element fieldset = ensureChild(doc, nestedNode, "field", "./" + nested.getFieldName());
                fieldset.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/form/fieldset");

                Element layout = ensureChild(doc, fieldset, "layout", null);
                layout.setAttribute("jcr:primaryType", "nt:unstructured");
                layout.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/layouts/fixedcolumns");
                layout.setAttribute("margin", "true");

                Element mfItems = ensureChild(doc, fieldset, "items", null);

                // Recursive call for nested multifield items
                handleNestedFields(doc, mfItems, nested.getNestedFields());

            } else {
                // Normal fields inside multifield (textfield, richtext, fileupload, select...)
                updateFieldNode(doc, parentItems, nested);
            }
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
        NodeList children = parent.getElementsByTagName(nodeName);
        if (children.getLength() > 0) {
            return (Element) children.item(0);
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
        NodeList childNodes = itemsElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String nameAttr = el.getAttribute("name");
                String fileRefAttr = el.getAttribute("fileReferenceParameter");

                boolean shouldRemove = false;

                if (nameAttr != null && !nameAttr.isEmpty()) {
                    if (!incomingNames.contains(nameAttr.replace("./", ""))) {
                        shouldRemove = true;
                    }
                } else if (fileRefAttr != null && !fileRefAttr.isEmpty()) {
                    if (!incomingNames.contains(fileRefAttr.replace("./", ""))) {
                        shouldRemove = true;
                    }
                }

                if (shouldRemove) {
                    itemsElement.removeChild(el);  // ✅ remove missing field
                    i--; // adjust loop since node list is live
                } else {
                    // ✅ check if this element has nested <items> (multifield case)
                    NodeList nestedItems = el.getElementsByTagName("items");
                    if (nestedItems != null && nestedItems.getLength() > 0) {
                        for (int j = 0; j < nestedItems.getLength(); j++) {
                            Element nested = (Element) nestedItems.item(j);
                            cleanUpDeletedFields(nested, incomingNames); // recursion
                        }
                    }
                }
            }
        }
    }


    // -------------------- update Sling model --------------------
    //sling model update


    public static void updateSlingModel(ComponentRequest request) throws IOException {
        // 1. Derive HTL path
        Path htlPath = Paths.get("generated-projects", request.getProjectName(),
                "ui.apps/src/main/content/jcr_root/apps",
                request.getProjectName(), "components", request.getComponentName(),
                request.getComponentName() + ".html");

        // 2. Extract Sling Model class name from HTL
        String htlContent = Files.readString(htlPath);
        String slingModelClass = extractSlingModelClass(htlContent);
        if (slingModelClass == null) {
            throw new RuntimeException("❌ No Sling Model found in HTL: " + htlPath);
        }

        // 3. Locate Sling Model .java file
        Path javaFilePath = locateJavaFile(request.getProjectName(), slingModelClass);
        if (javaFilePath == null) {
            throw new RuntimeException("❌ Could not find Java file for model: " + slingModelClass);
        }

        // 4. Determine base package for generating nested multifield classes
        String basePackage = slingModelClass.substring(0, slingModelClass.lastIndexOf("."));

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

        String content = Files.readString(javaFile);

        // 1️⃣ Extract existing fields
        Map<String, String> existingFields = extractFieldMap(content);

        // 2️⃣ Remove fields that no longer exist
        for (String fieldName : new HashSet<>(existingFields.keySet())) {
            if (fields.stream().noneMatch(f -> f.getFieldName().equals(fieldName))) {
                content = removeField(content, fieldName);
            }
        }

        // 3️⃣ Add or update fields
        for (ComponentField field : fields) {
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
    }

    private static String insertField(String content, ComponentField field,
                                      String packageName, String projectName) {

        String capName = capitalize(field.getFieldName());

        // 1️⃣ Multifield / child element → List<NestedClass>
        if ("multifield".equalsIgnoreCase(field.getFieldType()) ||
                "child".equalsIgnoreCase(field.getFieldType())) {

            String nestedClassName = capName;
            String fieldCode =
                    "    @ChildResource\n" +
                            "    private List<" + nestedClassName + "> " + field.getFieldName() + ";\n\n" +
                            "    public List<" + nestedClassName + "> get" + nestedClassName + "() {\n" +
                            "        return " + field.getFieldName() + ";\n" +
                            "    }\n\n";

            int insertPos = content.lastIndexOf("}");
            return content.substring(0, insertPos) + fieldCode + "}\n";
        }

        // 2️⃣ Single-value field → ValueMapValue
        String type = mapFieldTypeToJavaType(field.getFieldType());
        String fieldCode =
                "    @ValueMapValue\n" +
                        "    private " + type + " " + field.getFieldName() + ";\n\n" +
                        "    public " + type + " get" + capName + "() {\n" +
                        "        return " + field.getFieldName() + ";\n" +
                        "    }\n\n";

        int insertPos = content.lastIndexOf("}");
        return content.substring(0, insertPos) + fieldCode + "}\n";
    }


    private static boolean fieldExists(String content, String fieldName) {
        Pattern pField = Pattern.compile("private\\s+[\\w<>\\[\\]]+\\s+" + fieldName + "\\s*;");
        Pattern pGetter = Pattern.compile("public\\s+[\\w<>\\[\\]]+\\s+get" + capitalize(fieldName) + "\\s*\\(");
        return pField.matcher(content).find() || pGetter.matcher(content).find();
    }


    private boolean clsIsMultifield(Path file) {
        try {
            String content = Files.readString(file);
            return content.contains("@ValueMapValue"); // heuristic: multifield child classes always have VMV fields
        } catch (IOException e) {
            return false;
        }
    }

    // Extract all @ValueMapValue / @ChildResource fields
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


    // Remove a field + its getter
    // Remove a field and its getter
    private static String removeField(String content, String fieldName) {
        // Remove annotated field
        content = content.replaceAll(
                "(?s)@(ValueMapValue|ChildResource)\\s+private[^{;]+\\s+" + fieldName + "\\s*;\\s*",
                ""
        );

        /*// Remove ALL getters for this field
        content = content.replaceAll(
                "(?s)public\\s+[\\w<>\\[\\]]+\\s+get" + capitalize(fieldName) +
                        "\\s*\\(\\)\\s*\\{.*?\\}",
                ""
        );*/

        // Remove ALL getters for this field
        content = content.replaceAll(
                "(?s)public\\s+[\\w<>\\[\\]]+\\s+(get|is)" + capitalize(fieldName) +
                        "\\s*\\(\\)\\s*\\{.*?\\}",
                ""
        );

        return content;
    }


    // Insert new field + getter before last }
    private String insertNewField(String content, ComponentField field,
                                  String packageName, String projectName) {
        String cap = capitalize(field.getFieldName());

        String fieldCode;

        if ("multifield".equalsIgnoreCase(field.getFieldType())) {
            // ➡ Use List<ClassName> for multifields
            String nestedClassName = capitalize(field.getFieldName());
            fieldCode =
                    "    @ChildResource\n" +
                            "    private List<" + nestedClassName + "> " + field.getFieldName() + ";\n\n" +
                            "    public List<" + nestedClassName + "> get" + cap + "() {\n" +
                            "        return " + field.getFieldName() + ";\n" +
                            "    }\n\n";

            // 🔄 Generate the nested class
            try {
                generateOrUpdateMultifieldClass(projectName, packageName, nestedClassName, field.getNestedFields());
            } catch (IOException e) {
                throw new RuntimeException("Failed to generate multifield class for " + nestedClassName, e);
            }

        } else {
            // Normal field → map type
            String type = mapFieldTypeToJavaType(field.getFieldType());
            fieldCode =
                    "    @ValueMapValue\n" +
                            "    private " + type + " " + field.getFieldName() + ";\n\n" +
                            "    public " + type + " get" + cap + "() {\n" +
                            "        return " + field.getFieldName() + ";\n" +
                            "    }\n\n";
        }

        // Prevent duplicates
        if (content.contains("private " + field.getFieldName())) {
            return content;
        }

        int insertPos = content.lastIndexOf("}");
        return content.substring(0, insertPos) + fieldCode + "}\n";
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


    // Rebuild isEmpty()

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
                if ("multifield".equalsIgnoreCase(nested.getFieldType()) ||
                        "child".equalsIgnoreCase(nested.getFieldType())) {

                    generateOrUpdateMultifieldClass(projectName, packageName,
                            capitalize(nested.getFieldName()), nested.getNestedFields());
                }
            }
        }

        // Rebuild isEmpty
        content = updateIsEmpty(content, nestedFields);

        Files.writeString(javaFile, content);
    }


    /**
     * Inserts or updates a normal (non-multifield) field
     */
    private String insertOrUpdateSingleField(String content, ComponentField field,
                                             String packageName, String projectName) {
        String capName = capitalize(field.getFieldName());
        String type = mapFieldTypeToJavaType(field.getFieldType());

        if (fieldExists(content, field.getFieldName())) {
            return content.replaceAll("private\\s+[\\w<>\\[\\]]+\\s+" + field.getFieldName() + "\\s*;",
                    "private " + type + " " + field.getFieldName() + ";");
        }

        String fieldCode =
                "    @ValueMapValue\n" +
                        "    private " + type + " " + field.getFieldName() + ";\n\n" +
                        "    public " + type + " get" + capName + "() {\n" +
                        "        return " + field.getFieldName() + ";\n" +
                        "    }\n\n";

        int insertPos = content.lastIndexOf("}");
        return content.substring(0, insertPos) + fieldCode + "}\n";
    }

    private static String updateIsEmpty(String content, List<ComponentField> fields) {
        // Remove old isEmpty
        content = content.replaceAll("(?s)public\\s+boolean\\s+isEmpty\\s*\\(\\)\\s*\\{.*?\\}", "");

        StringBuilder checks = new StringBuilder();
        for (ComponentField f : fields) {
            if ("numberfield".equalsIgnoreCase(f.getFieldType())) {
                checks.append("        if (").append(f.getFieldName()).append(" != 0) empty = false;\n");
            } else if ("checkbox".equalsIgnoreCase(f.getFieldType())) {
                checks.append("        if (").append(f.getFieldName()).append(") empty = false;\n");
            } else {
                checks.append("        if (").append(f.getFieldName())
                        .append(" != null && !").append(f.getFieldName()).append(".toString().isEmpty()) " +
                                "empty = false;\n");
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


    // Map field type to Java type
    private String mapFieldType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "numberfield" -> "double";
            case "textfield", "textarea", "password", "pathfield" -> "String";
            case "tagfield", "multiselect" -> "List<String>";
            default -> "String"; // fallback
        };
    }

    private void generateMultifieldClass(Path classFile, String basePackage, String className, List<ComponentField>
            nestedFields) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(";\n\n")
                .append("import org.apache.sling.models.annotations.Model;\n")
                .append("import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n")
                .append("import java.util.*;\n")
                .append("import org.apache.sling.models.annotations.injectorsspecific.ValueMapValue;\n\n")
                .append("@Model(adaptables = org.apache.sling.api.resource.Resource.class, defaultInjectionStrategy" +
                        " = " +
                        "DefaultInjectionStrategy.OPTIONAL)\n")
                .append("public class ").append(className).append(" {\n");

//        for (ComponentField nested : nestedFields) {
//            sb.append("    @ValueMapValue\n")
//                    .append("    private String ").append(nested.getFieldName()).append(";\n\n");
//
//            sb.append("    public String get").append(capitalize(nested.getFieldName())).append("() {\n")
//                    .append("        return ").append(nested.getFieldName()).append(";\n")
//                    .append("    }\n\n");
//        }
        for (ComponentField nested : nestedFields) {
            String type = mapFieldTypeToJavaType(nested.getFieldType());
            sb.append("    @ValueMapValue\n")
                    .append("    private ").append(type).append(" ").append(nested.getFieldName()).append(";\n\n");

            sb.append("    public ").append(type).append(" get").append(capitalize(nested.getFieldName())).append("() {\n")
                    .append("        return ").append(nested.getFieldName()).append(";\n")
                    .append("    }\n\n");
        }


        sb.append("}\n");
        Files.writeString(classFile, sb.toString());
    }

    private void updateMultifieldClass(Path classFile, List<ComponentField> nestedFields) throws IOException {
        String content = Files.readString(classFile);
        StringBuilder newFields = new StringBuilder();

        for (ComponentField nested : nestedFields) {
            if (!content.contains("private String " + nested.getFieldName())) {
                newFields.append("    @ValueMapValue\n")
                        .append("    private String ").append(nested.getFieldName()).append(";\n\n");
                newFields.append("    public String get").append(capitalize(nested.getFieldName())).append("() {\n")
                        .append("        return ").append(nested.getFieldName()).append(";\n")
                        .append("    }\n\n");
            }
        }

        int insertPos = content.lastIndexOf("}");
        String updated = content.substring(0, insertPos) + newFields + "}\n";
        Files.writeString(classFile, updated);
    }

    private String removeField(String content, String fieldName, Path projectDir) throws IOException {
        // Remove the field declaration
        content = content.replaceAll(
                "(?s)@(ValueMapValue|ChildResource)\\s+private[^{;]+\\s+" + fieldName + "\\s*;\\s*", ""
        );

        // Remove getter(s)
        content = content.replaceAll(
                "(?s)public\\s+[\\w<>\\[\\]]+\\s+(get|is)" + capitalize(fieldName) + "\\s*\\(\\)\\s*\\{.*?\\}", ""
        );

        // Delete nested multifield class if exists
        Path nestedClass = projectDir.resolve(capitalize(fieldName) + ".java");
        if (Files.exists(nestedClass)) Files.delete(nestedClass);

        return content;
    }


    // -------------------- update HTL --------------------

    public static void updateHTL(String projectName, ComponentRequest request, String packageName) throws IOException {
        File htlFile = new File(PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components/" + request.getComponentName() + "/" + request.getComponentName() + ".html");


        if (!htlFile.exists()) {
            System.out.println("HTL file not found: " + htlFile.getAbsolutePath());
            return;
        }

        StringBuilder fieldSnippets = new StringBuilder();
        for (ComponentField field : request.getFields()) {
            fieldSnippets.append(generateHTLSnippet(field, 1)).append("\n");
        }

        String htlContent =
                "<sly data-sly-use.model=\"" + packageName + "." + capitalize(request.getComponentName()) + "Model\" />\n" +
                        "<sly data-sly-use.placeholderTemplate=\"core/wcm/components/commons/v1/templates.html\"/>\n\n" +
                        "<sly data-sly-test.hasContent=\"${!model.empty}\">\n" +
                        fieldSnippets +
                        "</sly>\n" +
                        "<sly data-sly-call=\"${placeholderTemplate.placeholder @ isEmpty = !hasContent}\" />";

        Files.writeString(htlFile.toPath(), htlContent, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String generateHTLSnippet(ComponentField field, int level) {
        String name = field.getFieldName();
        String type = field.getFieldType().toLowerCase();

        switch (type) {
            case "fileupload":
                return indent(level) + "<sly data-sly-test=\"${model." + name + "}\">\n" +
                        indent(level + 1) + "<p>" + field.getFieldLabel() +
                        ": <img src=\"${model." + name + "}\" alt=\"" + field.getFieldLabel() +
                        "\" style=\"max-width:100%; height:auto;\"/></p>\n" +
                        indent(level) + "</sly>";

            case "textfield":
            case "title":
                return indent(level) + "<sly data-sly-test=\"${model." + name + "}\">\n" +
                        indent(level + 1) + "<p class=\"" + name + "\">${model." + name + "}</p>\n" +
                        indent(level) + "</sly>";
            case "textarea":
                return indent(level) + "<sly data-sly-test=\"${model." + name + "}\">\n" +
                        indent(level + 1) + "<div class=\"" + name + "\">${model." + name + "}</div>\n" +
                        indent(level) + "</sly>";
            case "richtext":
                return indent(level) + "<sly data-sly-test=\"${model." + name + "}\">\n" +
                        indent(level + 1) + "<div class=\"" + name + "\">${model." + name + " @context='html'}</div>\n" +
                        indent(level) + "</sly>";
            case "pathfield":
                return indent(level) + "<sly data-sly-test=\"${model." + name + "}\">\n" +
                        indent(level + 1) + "<p class=\"" + name + "\"><a href=\"${model." + name + "}\">${model." + name + "}</a></p>\n" +
                        indent(level) + "</sly>";
            case "checkbox":
                return indent(level) + "<sly data-sly-test=\"${model." + name + "}\">\n"
                        + indent(level + 1) + "<p>" + field.getFieldLabel()
                        + ": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n"
                        + indent(level) + "</sly>";

            case "multifield":
                StringBuilder mf = new StringBuilder();
                mf.append(indent(level)).append("<sly data-sly-test=\"${model.").append(name).append("}\">\n");
                mf.append(indent(level + 1)).append("<ul data-sly-list.item=\"${model.").append(name).append("}\">\n");
                mf.append(indent(level + 2)).append("<li>\n");

                if (field.getNestedFields() != null && !field.getNestedFields().isEmpty()) {
                    for (ComponentField child : field.getNestedFields()) {
                        mf.append(generateHTLSnippetForChild(child, "item", level + 3)).append("\n");
                    }
                } else {
                    mf.append(indent(level + 3)).append("${item}\n");
                }

                mf.append(indent(level + 2)).append("</li>\n");
                mf.append(indent(level + 1)).append("</ul>\n");
                mf.append(indent(level)).append("</sly>");
                return mf.toString();
            default:
                return indent(level) + "<sly data-sly-test=\"${model." + name + "}\">\n" +
                        indent(level + 1) + "<div class=\"" + name + "\">${model." + name + "}</div>\n" +
                        indent(level) + "</sly>";
        }
    }


    private static String generateHTLSnippetForChild(ComponentField field, String parent, int level) {
        String name = field.getFieldName();
        String type = field.getFieldType().toLowerCase();

        switch (type) {
            case "fileupload":
                return indent(level) + "<sly data-sly-test=\"${" + parent + "." + name + "}\">\n" +
                        indent(level + 1) + "<img src=\"${" + parent + "." + name + "}\" alt=\"" + name + "\"/>\n" +
                        indent(level) + "</sly>";
            case "textfield":
            case "title":
                return indent(level) + "<sly data-sly-test=\"${" + parent + "." + name + "}\">\n" +
                        indent(level + 1) + "<p class=\"" + name + "\">${" + parent + "." + name + "}</p>\n" +
                        indent(level) + "</sly>";
            case "textarea":
                return indent(level) + "<sly data-sly-test=\"${" + parent + "." + name + "}\">\n" +
                        indent(level + 1) + "<div class=\"" + name + "\">${" + parent + "." + name + "}</div>\n" +
                        indent(level) + "</sly>";
            case "richtext":
                return indent(level) + "<sly data-sly-test=\"${" + parent + "." + name + "}\">\n" +
                        indent(level + 1) + "<div class=\"" + name + "\">${" + parent + "." + name + " @context='html'}</div>\n" +
                        indent(level) + "</sly>";
            case "checkbox":
                return indent(level) + "<sly data-sly-test=\"${" + parent + "." + name + "}\">\n" +
                        indent(level + 1) + "<div class=\"" + name + "\">${" + parent + "." + name + "}</div>\n" +
                        indent(level) + "</sly>";
            case "pathfield":
                return indent(level) + "<sly data-sly-test=\"${" + parent + "." + name + "}\">\n" +
                        indent(level + 1) + "<p class=\"" + name + "\"><a href=\"${" + parent + "." + name + "}\">${" + parent + "." + name + "}</a></p>\n" +
                        indent(level) + "</sly>";
            default:
                return indent(level) + "<sly data-sly-test=\"${" + parent + "." + name + "}\">\n" +
                        indent(level + 1) + "<div class=\"" + name + "\">${" + parent + "." + name + "}</div>\n" +
                        indent(level) + "</sly>";
        }
    }

    private static String indent(int level) {
        return "    ".repeat(level); // 4 spaces per level
    }


}
