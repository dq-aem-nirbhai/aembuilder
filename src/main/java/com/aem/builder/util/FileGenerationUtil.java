package com.aem.builder.util;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.model.Enum.FieldType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utility class for generating AEM component files, dialogs, and models.
 */
@Slf4j
public class FileGenerationUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileGenerationUtil.class);

    /**
     * Generates all files required for a component in the given project.
     */
    public static void generateAllFiles(String projectName, ComponentRequest request) {
        logger.info("FILEGEN: Starting file generation for project: {}", projectName);
        try {
            String appsRoot = "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps";
            File appsDir = new File( "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps");

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

            log.info("ModelPath{}",modelPath);


            // Get full model base path
            String modelBasePath = modelPath.toString();

            log.info("ModelBasePath{}",modelBasePath);

            // 5. Convert to Java package name
            String packageName = javaSourceRoot.relativize(modelPath).toString().replace(File.separatorChar, '.');

            log.info("PackageName {}",packageName);

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

        // Root structure
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

        // Buffer for all fields that are not in a tab
        List<ComponentField> mainTabFields = new ArrayList<>();

        // Generate top-level tabs
        for (ComponentField field : fields) {
            if ("tabs".equalsIgnoreCase(field.getFieldType())) {
                String tabNodeName = safeNodeName(field.getFieldName(), "tab");
                String tabTitle = (field.getFieldLabel() != null && !field.getFieldLabel().isBlank())
                        ? field.getFieldLabel() : tabNodeName;
                String resourceType = getResourceType(field.getFieldType());

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

                sb.append("            </items>\n")
                        .append("        </").append(tabNodeName).append(">\n");
            } else {
                mainTabFields.add(field);
            }
        }

        // Add a single "Main" tab for all non-tab fields, if any
        if (!mainTabFields.isEmpty()) {
            String resourceType = getResourceType("tabs");

            sb.append("        <main jcr:primaryType=\"nt:unstructured\"\n")
                    .append("            jcr:title=\"Main\"\n")
                    .append("            sling:resourceType=\"").append(resourceType).append("\">\n")
                    .append("            <items jcr:primaryType=\"nt:unstructured\">\n");

            for (ComponentField f : mainTabFields) {
                sb.append(generateFieldXml(safeNodeName(f.getFieldName(), "field"), f));
            }

            sb.append("            </items>\n")
                    .append("        </main>\n");
        }

        // Close XML
        sb.append("""
                    </items>
                </tabs>
            </items>
        </content>
    </jcr:root>
    """);

        // Write to file
        FileUtils.writeStringToFile(new File(dialogFolder + "/.content.xml"),
                sb.toString(), StandardCharsets.UTF_8);

        logger.info("DIALOG: Dialog .content.xml generated at {}/.content.xml", dialogFolder);
    }

    /** Utility: safe XML node name */
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
                    case "richtext" ->
                            "    useFixedInlineToolbar=\"true\"\n    enableSourceEdit=\"true\"\n";
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

            case  "radiogroup" -> {
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
                    sb.append("    multiple=\"true\"\n");
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
        log.info("generateFields   {}",generatedFields);

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
                case "checkbox" ->
                        sb.append("    @ValueMapValue\n    private boolean ").append(name).append(";\n\n");
                case "multiselect", "tagfield" ->
                        sb.append("    @ValueMapValue\n    private List<String> ").append(name).append(";\n\n");
                case "numberfield" ->
                        sb.append("    @ValueMapValue\n    private double ").append(name).append(";\n\n");
                default ->
                        sb.append("    @ValueMapValue\n    private String ").append(name).append(";\n\n");
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

        FileUtils.writeStringToFile(new File(modelBasePath + "/" + className + ".java"), sb.toString(),
                StandardCharsets.UTF_8);
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
            case "checkbox" ->
                    sb.append("    public boolean is").append(getter).append("() {\n")
                            .append("        return ").append(name).append(";\n    }\n\n");
            case "multiselect", "tagfield" ->
                    sb.append("    public List<String> get").append(getter).append("() {\n")
                            .append("        return ").append(name).append(";\n    }\n\n");
            case "numberfield" ->
                    sb.append("    public double get").append(getter).append("() {\n")
                            .append("        return ").append(name).append(";\n    }\n\n");
            default ->
                    sb.append("    public String get").append(getter).append("() {\n")
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
}