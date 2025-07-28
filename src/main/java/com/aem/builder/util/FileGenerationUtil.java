package com.aem.builder.util;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.Enum.FieldType;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FileGenerationUtil {

    public static void generateAllFiles(String projectName, ComponentRequest request) {
        try {
            String basePath = "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components/";
            String modelBasePath = "generated-projects/" + projectName + "/core/src/main/java/com/" + projectName + "/core/models";
            String packageName = "com." + projectName + ".core.models";

            generateComponent(basePath, modelBasePath, packageName, request.getComponentName(), request.getComponentGroup(), request.getFields());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateComponent(String basePath, String modelBasePath, String packageName,
                                         String componentName, String componentGroup, List<ComponentField> fields) throws Exception {

        String componentFolder = basePath + "/" + componentName;
        String dialogFolder = componentFolder + "/_cq_dialog";

        new File(dialogFolder).mkdirs();

        generateComponentContentXml(componentFolder, componentName, componentGroup);
        generateHTL(componentFolder, fields, packageName, componentName);
        generateDialogContentXml(dialogFolder, componentName, fields);
        generateSlingModel(modelBasePath, packageName, componentName, fields);
    }

    private static void generateComponentContentXml(String folderPath, String componentName, String componentGroup) throws Exception {
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\"\n" +
                "          xmlns:cq=\"http://www.day.com/jcr/cq/1.0\"\n" +
                "          xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"\n" +
                "          jcr:primaryType=\"cq:Component\"\n" +
                "          jcr:title=\"" + componentName + "\"\n" +
                "          componentGroup=\"" + componentGroup + "\"\n" +
                "          sling:resourceSuperType=\"core/wcm/components/page\"/>";
        FileUtils.writeStringToFile(new File(folderPath + "/.content.xml"), content, StandardCharsets.UTF_8);
    }

    private static void generateHTL(String folderPath, List<ComponentField> fields, String packageName, String componentName) throws Exception {
        String modelClassName = capitalize(componentName) + "Model";
        StringBuilder sb = new StringBuilder();

        sb.append("<div data-sly-use.model=\"")
                .append(packageName).append(".").append(modelClassName).append("\">\n");

        for (ComponentField field : fields) {
            sb.append("  <p>").append(field.getFieldLabel()).append(": ${model.")
                    .append(field.getFieldName()).append("}</p>\n");
        }

        sb.append("</div>");

        String htlFilePath = folderPath + "/" + componentName + ".html";
        FileUtils.writeStringToFile(new File(htlFilePath), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void generateDialogContentXml(String dialogPath, String componentName, List<ComponentField> fields) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<jcr:root xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\"\n")
                .append("          xmlns:granite=\"http://www.adobe.com/jcr/granite/1.0\"\n")
                .append("          xmlns:cq=\"http://www.day.com/jcr/cq/1.0\"\n")
                .append("          xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"\n")
                .append("          xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n")
                .append("          jcr:primaryType=\"nt:unstructured\"\n")
                .append("          jcr:title=\"").append(componentName).append(" Dialog\"\n")
                .append("          sling:resourceType=\"cq/gui/components/authoring/dialog\"\n")
                .append("          extraClientlibs=\"[core.wcm.components.title.v2.editor]\"\n")
                .append("          helpPath=\"https://www.adobe.com/go/aem_cmp_title_v2\"\n")
                .append("          trackingFeature=\"core-components:title:v2\">\n")

                .append("  <content jcr:primaryType=\"nt:unstructured\"\n")
                .append("           sling:resourceType=\"granite/ui/components/coral/foundation/container\">\n")
                .append("    <items jcr:primaryType=\"nt:unstructured\">\n")
                .append("      <tabs jcr:primaryType=\"nt:unstructured\"\n")
                .append("            sling:resourceType=\"granite/ui/components/coral/foundation/tabs\"\n")
                .append("            maximized=\"{Boolean}true\">\n")
                .append("        <items jcr:primaryType=\"nt:unstructured\">\n")
                .append("          <properties jcr:primaryType=\"nt:unstructured\"\n")
                .append("                      jcr:title=\"Properties\"\n")
                .append("                      sling:resourceType=\"granite/ui/components/coral/foundation/container\"\n")
                .append("                      margin=\"{Boolean}true\">\n")
                .append("            <items jcr:primaryType=\"nt:unstructured\">\n")
                .append("              <columns jcr:primaryType=\"nt:unstructured\"\n")
                .append("                       sling:resourceType=\"granite/ui/components/coral/foundation/fixedcolumns\"\n")
                .append("                       margin=\"{Boolean}true\">\n")
                .append("                <items jcr:primaryType=\"nt:unstructured\">\n")
                .append("                  <column jcr:primaryType=\"nt:unstructured\"\n")
                .append("                          sling:resourceType=\"granite/ui/components/coral/foundation/container\">\n")
                .append("                    <items jcr:primaryType=\"nt:unstructured\">\n");

        // Dynamically add fields
        for (ComponentField field : fields) {
            String resourceType = FieldType.valueOf(field.getFieldType().toUpperCase()).getResourceType();
            String fieldName = field.getFieldName();
            String fieldLabel = field.getFieldLabel();

            sb.append("                      <").append(fieldName).append(" jcr:primaryType=\"nt:unstructured\"\n")
                    .append("                                   sling:resourceType=\"").append(resourceType).append("\"\n")
                    .append("                                   name=\"./").append(fieldName).append("\"\n")
                    .append("                                   fieldLabel=\"").append(fieldLabel).append("\"\n")
                    .append("                                   fieldDescription=\"Enter ").append(fieldLabel).append("\"/>\n");
        }

        sb.append("                    </items>\n") // End column/items
                .append("                  </column>\n") // End column
                .append("                </items>\n") // End columns/items
                .append("              </columns>\n") // End columns
                .append("            </items>\n") // End properties/items
                .append("          </properties>\n") // End properties tab
                .append("          <cq:styles jcr:primaryType=\"nt:unstructured\"\n")
                .append("                      sling:resourceType=\"granite/ui/components/coral/foundation/include\"\n")
                .append("                      path=\"/mnt/overlay/cq/gui/components/authoring/dialog/style/tab_edit/styletab\"/>\n")
                .append("        </items>\n") // End tabs/items
                .append("      </tabs>\n") // End tabs
                .append("    </items>\n") // End content/items
                .append("  </content>\n")
                .append("</jcr:root>\n");

        FileUtils.writeStringToFile(new File(dialogPath + "/.content.xml"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void generateSlingModel(String modelBasePath, String packageName, String componentName, List<ComponentField> fields) throws Exception {
        String className = capitalize(componentName) + "Model";
        File modelDir = new File(modelBasePath);
        modelDir.mkdirs();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n")
                .append("import org.apache.sling.api.resource.Resource;\n")
                .append("import javax.inject.Inject;\n")
                .append("import org.apache.sling.models.annotations.Model;\n")
                .append("import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n")
                .append("import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;\n\n")
                .append("@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)\n")
                .append("public class ").append(className).append(" {\n\n");

        for (ComponentField field : fields) {
            String fieldName = field.getFieldName();
            String capitalized = capitalize(fieldName);

            sb.append("    @ValueMapValue\n")
                    .append("    private String ").append(fieldName).append(";\n\n")
                    .append("    public String get").append(capitalized).append("() {\n")
                    .append("        return ").append(fieldName).append(";\n")
                    .append("    }\n\n");
        }

        sb.append("}\n");

        FileUtils.writeStringToFile(new File(modelBasePath + "/" + className + ".java"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String capitalize(String input) {
        return (input == null || input.isEmpty())
                ? input
                : input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static String readFile(File file) {
        try {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
