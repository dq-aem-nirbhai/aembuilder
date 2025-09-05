package com.aem.builder.service.impl;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.service.UpdateComponent;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


@Slf4j
@Service
public class UpdateComponentImpl implements UpdateComponent {

    @Override
    public void updateDialog(File xmlFile, List<ComponentField> newFields) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringComments(true);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        // Locate <column><items>
        NodeList columns = doc.getElementsByTagName("column");
        if (columns.getLength() == 0) {
            columns = doc.getElementsByTagName("columns");        }
        Element column = (Element) columns.item(0);
        Element items = getOrCreateChild(column, "items", null);
        items.setAttribute("jcr:primaryType", "nt:unstructured");

        // --- Sync old XML with newFields ---
        syncFields(items, newFields);

        // Save back to file
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
    }

    /**
     * Compare existing XML children with newFields → remove, update, add
     */
    private void syncFields(Element parent, List<ComponentField> newFields) {
        // 1. Remove fields that are no longer present
        NodeList children = parent.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) node;
            String fieldName = el.getAttribute("name");
            String use=fieldName;
            if (fieldName.startsWith("./")) {
                fieldName = fieldName.substring(2);
            }
            boolean stillExists = newFields.stream()
                    .anyMatch(f -> f.getFieldName().equals(stripDotSlash(use)));

            if (!stillExists) {
                parent.removeChild(node);
            }
        }

        // 2. Add or update new fields
        for (ComponentField field : newFields) {
            Element existing = findChildByFieldName(parent, field.getFieldName());

            if (existing == null) {
                // Add new
                insertField(parent, field);
            } else {
                // Update existing
                updateField(existing, field);
            }
        }
    }
    private String stripDotSlash(String name) {
        if (name != null && name.startsWith("./")) {
            return name.substring(2);
        }
        return name;
    }

    /** Insert new field into dialog XML */
    private void insertField(Element parent, ComponentField field) {
        Document doc = parent.getOwnerDocument();
        if ("richtext".equalsIgnoreCase(field.getFieldType())) {
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("jcr:primaryType", "nt:unstructured");
            el.setAttribute("sling:resourceType", "cq/gui/components/authoring/dialog/richtext");
            el.setAttribute("fieldLabel", field.getFieldLabel());
            el.setAttribute("name", "./" + field.getFieldName());
            el.setAttribute("useFixedInlineToolbar", "true");
            el.setAttribute("enableSourceEdit", "true");

            parent.appendChild(el);
        }
        if("checkbox".equalsIgnoreCase(field.getFieldType())){
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("text", field.getFieldName());
            el.setAttribute("value","true");
            el.setAttribute("uncheckedValue","false");
            parent.appendChild(el);
        }
        if("fileupload".equalsIgnoreCase(field.getFieldType())){
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("fileReferenceParameter", field.getFieldName());
            el.setAttribute("class","cq-droptarget");
            el.setAttribute("mimeTypes","[image/gif,image/jpeg,image/png,image/tiff,image/svg+xml]");
            el.setAttribute("multiple","{Boolean}false");
            el.setAttribute("uploadUrl","/content/dam");
            el.setAttribute("fileNameParameter","./"+field.getFieldName());
            el.setAttribute("autoStart","{Boolean}false");
            parent.appendChild(el);
        }
        if ("multifield".equalsIgnoreCase(field.getFieldType())) {
            Element multifield = doc.createElement(field.getFieldName());
            multifield.setAttribute("jcr:primaryType", "nt:unstructured");
            multifield.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/" +
                    "form/multifield");
            multifield.setAttribute("fieldLabel", field.getFieldLabel());
            multifield.setAttribute("composite", "true");

            Element fieldNode = doc.createElement("field");
            fieldNode.setAttribute("jcr:primaryType", "nt:unstructured");
            fieldNode.setAttribute("sling:resourceType", "granite/ui/components/coral/foundation/" +
                    "form/fieldset");
            fieldNode.setAttribute("name", "./" + field.getFieldName());
            multifield.appendChild(fieldNode);

            Element items = doc.createElement("items");
            items.setAttribute("jcr:primaryType", "nt:unstructured");
            fieldNode.appendChild(items);

            parent.appendChild(multifield);

            if (field.getNestedFields() != null) {
                for (ComponentField nested : field.getNestedFields()) {
                    insertField(items, nested);
                }
            }
        }
        else {
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("jcr:primaryType", "nt:unstructured");
            el.setAttribute("sling:resourceType", getResourceType(field.getFieldType()));
            el.setAttribute("fieldLabel", field.getFieldLabel());
            el.setAttribute("name", "./" + field.getFieldName());

            // Options support
            if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                Element items = doc.createElement("items");
                items.setAttribute("jcr:primaryType", "nt:unstructured");
                for (int i = 0; i < field.getOptions().size(); i++) {
                    OptionItem opt = field.getOptions().get(i);
                    Element option = doc.createElement("option" + (i + 1));
                    option.setAttribute("jcr:primaryType", "nt:unstructured");
                    option.setAttribute("text", opt.getText());
                    option.setAttribute("value", opt.getValue());
                    items.appendChild(option);
                }
                el.appendChild(items);
            }

            parent.appendChild(el);
        }
    }

    /** Update existing field without removing */
    private void updateField(Element existing, ComponentField field) {
        existing.setAttribute("fieldLabel", field.getFieldLabel());
        existing.setAttribute("name", "./" + field.getFieldName());

        // Update type if changed
        existing.setAttribute("sling:resourceType", getResourceType(field.getFieldType()));
        if("fileupload".equalsIgnoreCase(field.getFieldType())){
            existing.setAttribute("fileReferenceParameter", field.getFieldName());
            existing.setAttribute("class","cq-droptarget");
            existing.setAttribute("mimeTypes","[image/gif,image/jpeg,image/png,image/tiff,image/svg+xml]");
            existing.setAttribute("multiple","{Boolean}false");
            existing.setAttribute("uploadUrl","/content/dam");
            existing.setAttribute("fileNameParameter","./"+field.getFieldName());
            existing.setAttribute("autoStart","{Boolean}false");
        }
        if("checkbox".equalsIgnoreCase(field.getFieldType())){

            existing.setAttribute("text", field.getFieldName());
            existing.setAttribute("value","true");
            existing.setAttribute("uncheckedValue","false");
        }
        if("richtext".equalsIgnoreCase(field.getFieldType())){
            existing.setAttribute("useFixedInlineToolbar","true");
            existing.setAttribute("enableSourceEdit","true");
        }
        // Handle multifield recursion
        if ("multifield".equalsIgnoreCase(field.getFieldType())) {
            Element fieldNode = getOrCreateChild(existing, "field",
                    "granite/ui/components/coral/foundation/form/fieldset");
            Element items = getOrCreateChild(fieldNode, "items", null);

            if (field.getNestedFields() != null) {
                for (ComponentField nested : field.getNestedFields()) {
                    Element child = findChildByFieldName(items, nested.getFieldName());
                    if (child != null) {
                        updateField(child, nested);   // update existing
                    } else {
                        insertField(items, nested);   // add new
                    }
                }
            }
        }





        // Handle select/multiselect options
        if ("select".equalsIgnoreCase(field.getFieldType()) ||
                "multiselect".equalsIgnoreCase(field.getFieldType())) {
            Element items = getOrCreateChild(existing, "items", null);
            // Clear old
            while (items.hasChildNodes()) {
                items.removeChild(items.getFirstChild());
            }
            if (field.getOptions() != null) {
                items.setAttribute("jcr:primaryType", "nt:unstructured");
                Document doc = existing.getOwnerDocument();
                for (int i = 0; i < field.getOptions().size(); i++) {
                    OptionItem opt = field.getOptions().get(i);
                    Element option = doc.createElement("option" + (i + 1));
                    option.setAttribute("jcr:primaryType", "nt:unstructured");
                    option.setAttribute("text", opt.getText());
                    option.setAttribute("value", opt.getValue());
                    items.appendChild(option);
                }
            }
            // Special case: multiselect needs attribute multiple="true"
            if ("multiselect".equalsIgnoreCase(field.getFieldType())) {
                existing.setAttribute("multiple", "true");
            } else {
                // ensure single select doesn’t accidentally keep the attribute
                existing.removeAttribute("multiple");
                existing.setAttribute("emptyText","Select...");
            }
        }
    }

    /** Utility: find child by fieldName (./name) */
    private Element findChildByFieldName(Element parent, String fieldName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String name = el.getAttribute("name");
            if (name != null && name.equals("./" + fieldName)) {
                return el;
            }
        }
        return null;
    }

    /** Utility: get or create child */
    private Element getOrCreateChild(Element parent, String name, String resourceType) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                return (Element) n;
            }
        }
        Document doc = parent.getOwnerDocument();
        Element child = doc.createElement(name);
        parent.appendChild(child);
        if (resourceType != null) {
            child.setAttribute("sling:resourceType", resourceType);
        }
        return child;
    }

    /** Field type → sling:resourceType mapping */
    private String getResourceType(String type) {
        return switch (type.toLowerCase()) {
            case "textfield"    -> "granite/ui/components/coral/foundation/form/textfield";
            case "textarea"     -> "granite/ui/components/coral/foundation/form/textarea";
            case "numberfield"  -> "granite/ui/components/coral/foundation/form/numberfield";
            case "pathfield"    -> "granite/ui/components/coral/foundation/form/pathfield";
            case "datepicker"   -> "granite/ui/components/coral/foundation/form/datepicker";
            case "tagfield"     -> "granite/ui/components/coral/foundation/form/tagfield";
            case "richtext"     -> "cq/gui/components/authoring/dialog/richtext";
            case "switch"       -> "granite/ui/components/coral/foundation/form/switch";
            case "colorfield"   -> "granite/ui/components/coral/foundation/form/colorfield";
            case "fileupload", "image" -> "cq/gui/components/authoring/dialog/fileupload";
            case "select", "multiselect" -> "granite/ui/components/coral/foundation/form/select";
            case "password"    -> "granite/ui/components/coral/foundation/form/password";
            case "checkbox"    -> "granite/ui/components/coral/foundation/form/checkbox";
            case "tabs"        -> "granite/ui/components/coral/foundation/tabs";
            case "radiogroup"  ->"granite/ui/components/coral/foundation/form/radiogroup";
            default -> "granite/ui/components/coral/foundation/form/textfield"; // fallback
        };
    }









    //sling model update

    @Override
    public void updateSlingModel(ComponentRequest request) throws IOException {
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


    private String extractSlingModelClass(String htlContent) {
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


    private Path locateJavaFile(String projectName, String slingModelClass) {
        String relativePath = slingModelClass.replace(".", "/") + ".java";
        Path javaPath = Paths.get("generated-projects", projectName,
                "core/src/main/java", relativePath);
        return Files.exists(javaPath) ? javaPath : null;
    }


    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }


    /**
     * Patch-update Sling Model file based on ComponentRequest fields.
     */
    private void patchSlingModel(Path javaFile, List<ComponentField> fields,
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
    private String insertField(String content, ComponentField field,
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



    private boolean fieldExists(String content, String fieldName) {
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
    private Map<String, String> extractFieldMap(String content) {
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
    private String removeField(String content, String fieldName) {
        // Remove annotated field
        content = content.replaceAll(
                "(?s)@(ValueMapValue|ChildResource)\\s+private[^{;]+\\s+" + fieldName + "\\s*;\\s*",
                ""
        );

        // Remove ALL getters for this field
        content = content.replaceAll(
                "(?s)public\\s+[\\w<>\\[\\]]+\\s+get" + capitalize(fieldName) +
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
    private String updateField(String content, ComponentField field) {
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

        // 1️⃣ Update field declaration including annotation
        content = content.replaceAll(
                "(?s)@(?:ValueMapValue|ChildResource)\\s+private[^{;]+\\s+" + fieldName + "\\s*;",
                annotation + " private " + type + " " + fieldName + ";"
        );

        // 2️⃣ Update getter return type
        content = content.replaceAll(
                "(?s)public\\s+[\\w<>\\[\\]]+\\s+get" + capName + "\\s*\\(\\)",
                "public " + type + " get" + capName + "()"
        );

        return content;
    }




    // Rebuild isEmpty()

    /**
     * Recursive generator for multifield classes
     */
    private void generateOrUpdateMultifieldClass(String projectName, String packageName,
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

    private String updateIsEmpty(String content, List<ComponentField> fields) {
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
    // Utility: map AEM field types to Java types
    private String mapFieldTypeToJavaType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "numberfield" -> "double";
            case "checkbox" -> "boolean";
            case "textfield", "textarea", "password", "fileupload", "pathfield" -> "String";
            default -> "String"; // fallback
        };
    }


    // Map field type to Java type
    private String mapFieldType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "numberfield" -> "double";
            case "textfield", "textarea", "password", "pathfield" -> "String";
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

        for (ComponentField nested : nestedFields) {
            sb.append("    @ValueMapValue\n")
                    .append("    private String ").append(nested.getFieldName()).append(";\n\n");

            sb.append("    public String get").append(capitalize(nested.getFieldName())).append("() {\n")
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






    //update htl
    @Override
    public void updateHTLTextOnly(ComponentRequest request, ComponentRequest oldRequest) throws IOException {
        Path htlPath = Path.of("generated-projects", request.getProjectName(),
                "ui.apps/src/main/content/jcr_root/apps",
                request.getProjectName(), "components", request.getComponentName(),
                request.getComponentName() + ".html");

        if (!Files.exists(htlPath)) {
            throw new IllegalStateException("HTL file not found: " + htlPath);
        }

        String htlContent = Files.readString(htlPath);

        // 1️⃣ Remove deleted fields
        for (ComponentField oldField : oldRequest.getFields()) {
            boolean stillExists = request.getFields().stream()
                    .anyMatch(f -> f.getFieldName().equals(oldField.getFieldName()));
            if (!stillExists) {
                htlContent = htlContent.replaceAll(
                        "(?s)<.*?\\$\\{model\\." + Pattern.quote(oldField.getFieldName()) + ".*?>.*?</.*?>",
                        ""
                );
            }
        }

        // 2️⃣ Rename fields if the name changed
        for (ComponentField oldField : oldRequest.getFields()) {
            for (ComponentField newField : request.getFields()) {
                if (!oldField.getFieldName().equals(newField.getFieldName()) &&
                        oldField.getFieldLabel().equals(newField.getFieldLabel())) {
                    htlContent = htlContent.replaceAll(
                            "\\$\\{model\\." + Pattern.quote(oldField.getFieldName()) + "\\}",
                            "\\${model." + newField.getFieldName() + "}"
                    );
                }
            }
        }

        // 3️⃣ Generate snippets for new fields
        StringBuilder newFieldsSnippets = new StringBuilder();
        for (ComponentField newField : request.getFields()) {
            boolean alreadyExists = oldRequest.getFields().stream()
                    .anyMatch(f -> f.getFieldName().equals(newField.getFieldName()));
            if (!alreadyExists) {
                newFieldsSnippets.append(generateHTLSnippet(newField));
            }
        }

        // 4️⃣ Insert new fields inside the hasContent sly block (pure string manipulation)
        if (newFieldsSnippets.length() > 0) {
            Pattern slyBlockPattern = Pattern.compile(
                    "(<sly[^>]*data-sly-test\\.hasContent[^>]*>)(.*?)(</sly>)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = slyBlockPattern.matcher(htlContent);
            if (matcher.find()) {
                String openingTag = matcher.group(1);
                String innerContent = matcher.group(2);
                String closingTag = matcher.group(3);

                // Append new fields inside the block
                innerContent += newFieldsSnippets.toString();

                // Rebuild the sly block
                htlContent = htlContent.substring(0, matcher.start()) +
                        openingTag + innerContent + closingTag +
                        htlContent.substring(matcher.end());
            } else {
                // fallback: append at the end before closing </sly> if block not found
                int lastSlyIndex = htlContent.lastIndexOf("</sly>");
                if (lastSlyIndex != -1) {
                    htlContent = htlContent.substring(0, lastSlyIndex)
                            + newFieldsSnippets.toString()
                            + htlContent.substring(lastSlyIndex);
                } else {
                    // fallback: append at the very end
                    htlContent += newFieldsSnippets.toString();
                }
            }
        }

        // 5️⃣ Save back
        Files.writeString(htlPath, htlContent);
    }


    // Helper to generate HTML snippet based on field type
    private String generateHTLSnippet(ComponentField field) {
        if ("multifield".equalsIgnoreCase(field.getFieldType()) || "child".equalsIgnoreCase(field.getFieldType())) {
            StringBuilder sb = new StringBuilder();
            sb.append("<div class=\"multifield\" data-sly-list.item=\"${model.")
                    .append(field.getFieldName()).append("}\">\n");

            if (field.getNestedFields() != null) {
                for (ComponentField nested : field.getNestedFields()) {
                    sb.append(generateHTLSnippetNested(nested, "item"));
                }
            }

            sb.append("</div>\n");
            return sb.toString();
        } else {
            return generateHTLSnippetNested(field, "model");
        }
    }

    private String generateHTLSnippetNested(ComponentField field, String modelRef) {
        return switch (field.getFieldType().toLowerCase()) {
            case "textfield", "numberfield", "pathfield" ->
                    "<input type=\"text\" data-sly-value=\"${" + modelRef + "." + field.getFieldName() + "}\" />\n";
            case "textarea" ->
                    "<textarea data-sly-text=\"${" + modelRef + "." + field.getFieldName() + "}\"></textarea>\n";
            case "checkbox" ->
                    "<input type=\"checkbox\" data-sly-checked=\"${" + modelRef + "." + field.getFieldName() + "}\" />\n";
            case "select", "multiselect" -> {
                StringBuilder sb = new StringBuilder("<select data-sly-list.option=\"${" +
                        modelRef + "." + field.getFieldName() + "}\">\n");
                if (field.getOptions() != null) {
                    for (OptionItem option : field.getOptions()) {
                        sb.append("  <option value=\"").append(option.getValue()).append("\">")
                                .append(option.getText()).append("</option>\n");
                    }
                }
                sb.append("</select>\n");
                yield sb.toString();
            }
            default ->
                    "<p data-sly-text=\"${" + modelRef + "." + field.getFieldName() + "}\"></p>\n";
        };
    }

}