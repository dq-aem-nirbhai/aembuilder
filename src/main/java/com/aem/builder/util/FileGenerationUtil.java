package com.aem.builder.util;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.Enum.FieldType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for generating AEM component files, dialogs, and models.
 */
public class FileGenerationUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileGenerationUtil.class);

    /**
     * Generates all files required for a component in the given project.
     */
    public static void generateAllFiles(String projectName, ComponentRequest request) {
        logger.info("FILEGEN: Starting file generation for project: {}", projectName);
        try {
            String basePath = "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                    + projectName + "/components/";
            String modelBasePath = "generated-projects/" + projectName + "/core/src/main/java/com/" + projectName
                    + "/core/models";
            String packageName = "com." + projectName + ".core.models";

            generateComponent(basePath, modelBasePath, packageName, request.getComponentName(),
                    request.getComponentGroup(), request.getFields(), request.isProxyComponent(),
                    request.getResourceSuperType(), request.getExtraClientlibs(), request.getHelpPath(),
                    request.getTrackingFeature(), request.isShowOnCreate());
            logger.info("FILEGEN: Successfully generated all files for project: {}", projectName);
        } catch (Exception e) {
            logger.info("FILEGEN: Error generating files for project: {}", projectName, e);
            e.printStackTrace();
        }
    }

    /**
     * Generates component folders, content.xml, HTL, dialog, and Sling model.
     */
    public static void generateComponent(String basePath, String modelBasePath, String packageName,
            String componentName, String componentGroup, List<ComponentField> fields, boolean proxy,
            String resourceSuperType, String extraClientlibs, String helpPath, String trackingFeature,
            boolean showOnCreate) throws Exception {
        logger.info("COMPONENT: Generating component '{}'", componentName);

        String componentFolder = basePath + "/" + componentName;
        String dialogFolder = componentFolder + "/_cq_dialog";

        new File(dialogFolder).mkdirs();

        generateComponentContentXml(componentFolder, componentName, componentGroup, proxy, resourceSuperType);
        generateHTL(componentFolder, fields, packageName, componentName);
        generateDialogContentXml(componentName, dialogFolder, fields, extraClientlibs, helpPath, trackingFeature, showOnCreate);
        generateSlingModel(modelBasePath, packageName, componentName, fields);

        logger.info("COMPONENT: Finished generating component '{}'", componentName);
    }

    /**
     * Generates the .content.xml for the component.
     */
    private static void generateComponentContentXml(String folderPath, String componentName, String componentGroup,
            boolean proxy, String resourceSuperType) throws Exception {
        logger.info("CONTENTXML: Generating .content.xml for component '{}'", componentName);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<jcr:root xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\"\n")
                .append("          xmlns:cq=\"http://www.day.com/jcr/cq/1.0\"\n")
                .append("          xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"\n")
                .append("          jcr:primaryType=\"cq:Component\"\n")
                .append("          jcr:title=\"").append(componentName).append("\"\n")
                .append("          componentGroup=\"").append(componentGroup).append("\"\n");
        if (proxy && resourceSuperType != null && !resourceSuperType.isBlank()) {
            sb.append("          sling:resourceSuperType=\"").append(resourceSuperType).append("\"\n");
        }
        sb.append("/>");
        String content = sb.toString();
        FileUtils.writeStringToFile(new File(folderPath + "/.content.xml"), content, StandardCharsets.UTF_8);
        logger.info("CONTENTXML: .content.xml generated at {}/.content.xml", folderPath);
    }

    /**
     * Generates the HTL (HTML Template Language) file for the component.
     */
    private static void generateHTL(String folderPath, List<ComponentField> fields, String packageName,
            String componentName) throws Exception {
        logger.info("HTL: Generating HTL for component '{}'", componentName);
        String modelClassName = capitalize(componentName) + "Model";
        StringBuilder sb = new StringBuilder();

        sb.append("<sly data-sly-use.model=\"")
                .append(packageName).append(".").append(modelClassName).append("\"/>\n")
                .append("<sly data-sly-use.placeholderTemplate=\"core/wcm/components/commons/v1/templates.html\"/>\n")
                .append("<sly data-sly-test.hasContent=\"${!model.empty}\">\n");

        for (ComponentField field : fields) {
            String fieldName = field.getFieldName();
            String fieldLabel = field.getFieldLabel();
            String fieldType = field.getFieldType().toLowerCase();

            // Log each field processed
            logger.info("HTL: Processing field '{}' of type '{}'", fieldName, fieldType);

            switch (fieldType) {
                case "multifield" -> {
                    sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append(" && model.")
                            .append(fieldName).append(".size > 0}\">\n")
                            .append("    <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n");
                    for (ComponentField nested : field.getNestedFields()) {
                        sb.append("      <li>").append(nested.getFieldLabel())
                                .append(": ${item.").append(nested.getFieldName()).append("}</li>\n");
                    }
                    sb.append("    </ul>\n")
                            .append("  </sly>\n");
                }
                case "checkbox" -> {
                    sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                            .append("    <p>").append(fieldLabel)
                            .append(": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n")
                            .append("  </sly>\n");
                }
                case "checkboxgroup", "multiselect" -> {
                    sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append(" && model.")
                            .append(fieldName).append(".size > 0}\">\n")
                            .append("    <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n")
                            .append("      <li>${item}</li>\n")
                            .append("    </ul>\n")
                            .append("  </sly>\n");
                }
                case "image", "fileupload" -> {
                    sb.append("  <sly data-sly-test=\"${model.fileReference}\">\n")
                            .append("    <p>").append(fieldLabel);
                    if (fieldType.equals("image")) {
                        sb.append(": <img src=\"${model.fileReference}\" alt=\"").append(fieldLabel)
                                .append("\" style=\"max-width:100%; height:auto;\"/></p>\n");
                    } else {
                        sb.append(": <a href=\"${model.fileReference}\" download>Download File</a></p>\n");
                    }
                    sb.append("  </sly>\n");
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

        FileUtils.writeStringToFile(new File(folderPath + "/" + componentName + ".html"), sb.toString(),
                StandardCharsets.UTF_8);
        logger.info("HTL: HTL file generated at {}/{}.html", folderPath, componentName);
    }

    /**
     * Generates the dialog .content.xml for the component.
     */
    public static void generateDialogContentXml(String componentName, String dialogFolder, List<ComponentField> fields,
            String extraClientlibs, String helpPath, String trackingFeature, boolean showOnCreate)
            throws Exception {
        logger.info("DIALOG: Generating dialog .content.xml for component '{}'", componentName);
        String dialogTitle = componentName + " Dialog";

        StringBuilder sb = new StringBuilder();
        String ec = extraClientlibs != null && !extraClientlibs.isBlank() ? "extraClientlibs=\"" + extraClientlibs + "\"" : "";
        String hp = helpPath != null && !helpPath.isBlank() ? "helpPath=\"" + helpPath + "\"" : "";
        String tf = trackingFeature != null && !trackingFeature.isBlank() ? "trackingFeature=\"" + trackingFeature + "\"" : "";
        String soc = showOnCreate ? "cq:showOnCreate=\"true\"" : "";

        sb.append(String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
                          xmlns:cq="http://www.day.com/jcr/cq/1.0"
                          xmlns:jcr="http://www.jcp.org/jcr/1.0"
                          xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                          jcr:primaryType="nt:unstructured"
                          jcr:title="%s"
                          sling:resourceType="cq/gui/components/authoring/dialog"
                          %s %s %s %s>
                <content
                  jcr:primaryType="nt:unstructured"
                  sling:resourceType="granite/ui/components/coral/foundation/container">
                    <items
                      jcr:primaryType="nt:unstructured">
                      <tabs
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/tabs">
                        <items
                          jcr:primaryType="nt:unstructured">
                          <tab1
                            jcr:primaryType="nt:unstructured"
                            jcr:title="Main"
                            sling:resourceType="granite/ui/components/coral/foundation/container">
                            <items
                              jcr:primaryType="nt:unstructured">
                """, dialogTitle, ec, hp, tf, soc));

        for (ComponentField field : fields) {
            String nodeName = field.getFieldName();
            logger.info("DIALOG: Adding dialog field '{}'", nodeName);
            sb.append(generateFieldXml(nodeName, field));
        }

        sb.append("""
                            </items>
                          </tab1>
                        </items>
                      </tabs>
                    </items>
                  </content>
                </jcr:root>
                """);

        FileUtils.writeStringToFile(new File(dialogFolder + "/.content.xml"), sb.toString(), StandardCharsets.UTF_8);
        logger.info("DIALOG: Dialog .content.xml generated at {}/.content.xml", dialogFolder);
    }

    /**
     * Generates XML for a single dialog field.
     */
    private static String generateFieldXml(String nodeName, ComponentField field) {
        // No logging here as it's called in a loop and already logged in the caller.
        String type = field.getFieldType().toLowerCase();
        String label = field.getFieldLabel();
        String name = field.getFieldName();
        List<String> options = field.getOptions();
        List<ComponentField> nested = field.getNestedFields();

        return switch (type) {
            case "textfield", "textarea", "numberfield", "hidden", "password",
                    "pathfield", "datepicker", "tagfield", "richtext", "switch", "anchorbrowser" -> {
                String resource = getResourceType(type);
                String extra = switch (type) {
                    case "textarea" -> "    rows=\"5\"\n";
                    case "numberfield" -> "    min=\"0\" max=\"1000\"\n";
                    case "tagfield" ->
                        "    autocompleter=\"true\"\n    multiple=\"true\"\n    rootPath=\"/content/cq:tags\"\n";
                    case "richtext" -> "    useFixedInlineToolbar=\"true\"\n    enableSourceEdit=\"true\"\n";
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

            case "checkboxgroup", "radiogroup" -> {
                String resource = type.equals("checkboxgroup")
                        ? getResourceType("checkboxgroup")
                        : getResourceType("radiogroup");
                StringBuilder sb = new StringBuilder();
                sb.append("  <").append(nodeName).append("\n")
                        .append("    jcr:primaryType=\"nt:unstructured\"\n")
                        .append("    sling:resourceType=\"").append(resource).append("\"\n")
                        .append("    fieldLabel=\"").append(label).append("\"\n")
                        .append("    name=\"./").append(name).append("\">\n")
                        .append("    <items jcr:primaryType=\"nt:unstructured\">\n");
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        sb.append("      <option").append(i + 1).append("\n")
                                .append("        jcr:primaryType=\"nt:unstructured\"\n")
                                .append("        text=\"").append(options.get(i)).append("\"\n")
                                .append("        value=\"").append(options.get(i)).append("\"/>\n");
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
                        sb.append("      <option").append(i + 1).append("\n")
                                .append("        jcr:primaryType=\"nt:unstructured\"\n")
                                .append("        text=\"").append(options.get(i)).append("\"\n")
                                .append("        value=\"").append(options.get(i)).append("\"/>\n");
                    }
                }
                sb.append("    </items>\n")
                        .append("  </").append(nodeName).append(">\n");
                yield sb.toString();
            }

            case "button" -> {
                yield String.format(
                        "  <%s\n" +
                                "    jcr:primaryType=\"nt:unstructured\"\n" +
                                "    sling:resourceType=\"%s\"\n" +
                                "    text=\"%s\"\n" +
                                "    icon=\"add\"\n" +
                                "    type=\"button\"/>\n",
                        nodeName, getResourceType(type), label);
            }
            case "image", "fileupload" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("  <").append(nodeName).append("\n")
                        .append("    jcr:primaryType=\"nt:unstructured\"\n")
                        .append("    sling:resourceType=\"").append(getResourceType(type)).append("\"\n")
                        .append("    autoStart=\"{Boolean}false\"\n")
                        .append("    class=\"cq-droptarget\"\n")
                        .append("    fileNameParameter=\"./fileName\"\n")
                        .append("    fileReferenceParameter=\"./fileReference\"\n")
                        .append("    mimeTypes=\"[image/gif,image/jpeg,image/png,image/tiff,image/svg+xml]\"\n")
                        .append("    multiple=\"{Boolean}false\"\n")
                        .append("    name=\"./").append(name).append("\"\n")
                        .append("    title=\"").append(label).append("\"\n")
                        .append("    uploadUrl=\"${request.contextPath}/content/dam\"/>\n");
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
     * Generates the Sling Model Java class for the component.
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

        // Add fields with logging
        for (ComponentField field : fields) {
            String name = field.getFieldName();
            String type = field.getFieldType().toLowerCase();
            logger.info("MODEL: Adding field '{}' of type '{}'", name, type);

            switch (type) {
                case "multifield" -> {
                    generateChildModelClass(modelBasePath, packageName, componentName, field);
                    sb.append("    @ChildResource\nprivate List<").append(capitalize(name)).append("> ").append(name)
                            .append(";\n\n");
                }
                case "checkbox" -> sb.append("    @ValueMapValue\nprivate boolean ").append(name).append(";\n\n");
                case "checkboxgroup", "multiselect" ->
                    sb.append("    @ValueMapValue\nprivate List<String> ").append(name).append(";\n\n");
                case "numberfield" -> sb.append("    @ValueMapValue\nprivate double ").append(name).append(";\n\n");
                case "image", "fileupload" -> sb.append("    @ValueMapValue\nprivate String fileReference;\n\n");
                default -> sb.append("    @ValueMapValue\nprivate String ").append(name).append(";\n\n");
            }
        }

        // Add getters
        for (ComponentField field : fields) {
            String name = field.getFieldName();
            String getter = capitalize(name);
            String type = field.getFieldType().toLowerCase();

            switch (type) {
                case "multifield" ->
                    sb.append("    public List<").append(getter).append("> get").append(getter).append("() {\n")
                            .append("        return ").append(name).append(";\n    }\n\n");
                case "checkbox" -> sb.append("    public boolean is").append(getter).append("() {\n")
                        .append("        return ").append(name).append(";\n    }\n\n");
                case "checkboxgroup", "multiselect" ->
                    sb.append("    public List<String> get").append(getter).append("() {\n")
                            .append("        return ").append(name).append(";\n    }\n\n");
                case "numberfield" -> sb.append("    public double get").append(getter).append("() {\n")
                        .append("        return ").append(name).append(";\n    }\n\n");
                case "image", "fileupload" -> sb.append("    public String getFileReference() {\n")
                        .append("        return fileReference;\n    }\n\n");
                default -> sb.append("    public String get").append(getter).append("() {\n")
                        .append("        return ").append(name).append(";\n    }\n\n");
            }
        }

        // Add isEmpty method
        sb.append("    public boolean isEmpty() {\n        return ");

        List<String> emptyChecks = new ArrayList<>();
        for (ComponentField field : fields) {
            String name = field.getFieldName();
            String type = field.getFieldType().toLowerCase();

            switch (type) {
                case "multifield", "checkboxgroup", "multiselect" ->
                    emptyChecks.add("(" + name + " == null || " + name + ".isEmpty())");
                case "checkbox" -> emptyChecks.add("!" + name);
                case "numberfield" -> emptyChecks.add(name + " == 0");
                case "image", "fileupload" -> emptyChecks.add("(fileReference == null || fileReference.isEmpty())");
                default -> emptyChecks.add("(" + name + " == null || " + name + ".isEmpty())");
            }
        }

        sb.append(String.join(" && ", emptyChecks)).append(";\n");
        sb.append("    }\n}");

        FileUtils.writeStringToFile(new File(modelBasePath + "/" + className + ".java"), sb.toString(),
                StandardCharsets.UTF_8);
        logger.info("MODEL: Sling Model generated at {}/{}.java", modelBasePath, className);
    }

    /**
     * Generates a child model class for multifield support.
     */
    private static void generateChildModelClass(String modelBasePath, String packageName, String componentName,
            ComponentField field) throws Exception {
        String className = capitalize(field.getFieldName());
        logger.info("CHILDMODEL: Generating child model class '{}'", className);
        File modelDir = new File(modelBasePath);
        modelDir.mkdirs();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n")
                .append("import javax.inject.Inject;\n")
                .append("import java.util.List;\n")
                .append("import org.apache.sling.api.resource.Resource;\n")
                .append("import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n")
                .append("import org.apache.sling.models.annotations.Model;\n")
                .append("import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;\n\n")
                .append("@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)\n")
                .append("public class ").append(className).append(" {\n\n");

        // Add fields with logging
        for (ComponentField subField : field.getNestedFields()) {
            String subName = subField.getFieldName();
            String type = subField.getFieldType().toLowerCase();
            logger.info("CHILDMODEL: Adding nested field '{}' of type '{}'", subName, type);

            switch (type) {
                case "checkbox" -> sb.append("    @ValueMapValue\n")
                        .append("    private boolean ").append(subName).append(";\n\n");
                case "numberfield" -> sb.append("    @ValueMapValue\n")
                        .append("    private double ").append(subName).append(";\n\n");
                case "checkboxgroup", "multiselect" -> sb.append("    @ValueMapValue\n")
                        .append("    private List<String> ").append(subName).append(";\n\n");
                case "image" -> sb.append("    @ValueMapValue\n")
                        .append("    private String fileReference;\n\n");
                default -> sb.append("    @ValueMapValue\n")
                        .append("    private String ").append(subName).append(";\n\n");
            }
        }

        // Add getters
        for (ComponentField subField : field.getNestedFields()) {
            String subName = subField.getFieldName();
            String getter = capitalize(subName);
            String type = subField.getFieldType().toLowerCase();

            switch (type) {
                case "checkbox" -> sb.append("    public boolean is").append(getter).append("() {\n")
                        .append("        return ").append(subName).append(";\n    }\n\n");
                case "numberfield" -> sb.append("    public double get").append(getter).append("() {\n")
                        .append("        return ").append(subName).append(";\n    }\n\n");
                case "checkboxgroup", "multiselect" ->
                    sb.append("    public List<String> get").append(getter).append("() {\n")
                            .append("        return ").append(subName).append(";\n    }\n\n");
                case "image" -> sb.append("    public String getFileReference() {\n")
                        .append("        return fileReference;\n    }\n\n");
                default -> sb.append("    public String get").append(getter).append("() {\n")
                        .append("        return ").append(subName).append(";\n    }\n\n");
            }
        }

        sb.append("}");

        FileUtils.writeStringToFile(new File(modelBasePath + "/" + className + ".java"), sb.toString(),
                StandardCharsets.UTF_8);
        logger.info("CHILDMODEL: Child model class generated at {}/{}.java", modelBasePath, className);
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
        logger.info("FILEGEN: Reading file '{}'", file.getPath());
        try {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.info("FILEGEN: Error reading file '{}'", file.getPath(), e);
            return "";
        }
    }
}