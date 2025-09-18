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
            el.setAttribute("sling:resourceType",getResourceType(field.getFieldType()));
            el.setAttribute("fieldLabel", field.getFieldLabel());
            el.setAttribute("name", "./" + field.getFieldName());
            el.setAttribute("useFixedInlineToolbar", "true");
            el.setAttribute("enableSourceEdit", "true");

            parent.appendChild(el);
            return;
        }
       else if("checkbox".equalsIgnoreCase(field.getFieldType())){
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("jcr:primaryType", "nt:unstructured");
            el.setAttribute("sling:resourceType", getResourceType(field.getFieldType()));
            el.setAttribute("fieldLabel", field.getFieldLabel());
            el.setAttribute("name", "./" + field.getFieldName());
            el.setAttribute("text", field.getFieldName());
            el.setAttribute("value","true");
            el.setAttribute("uncheckedValue","false");
            parent.appendChild(el);
        }
       else if("fileupload".equalsIgnoreCase(field.getFieldType())){
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("sling:resourceType",getResourceType(field.getFieldType()));
            el.setAttribute("jcr:primaryType", "nt:unstructured");
            el.setAttribute("fieldLabel", field.getFieldLabel());
            el.setAttribute("name", "./file");
            el.setAttribute("fileReferenceParameter", "./"+field.getFieldName());
            el.setAttribute("class","cq-droptarget");
            el.setAttribute("mimeTypes","[image/gif,image/jpeg,image/png,image/tiff,image/svg+xml]");
            el.setAttribute("multiple","{Boolean}false");
            el.setAttribute("uploadUrl","/content/dam");
            el.setAttribute("fileNameParameter","./"+field.getFieldName());
            el.setAttribute("autoStart","{Boolean}false");
            parent.appendChild(el);
        }
        else if ("tagfield".equalsIgnoreCase(field.getFieldType())) {
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("jcr:primaryType", "nt:unstructured");
            el.setAttribute("sling:resourceType", getResourceType(field.getFieldType()));
            el.setAttribute("fieldLabel", field.getFieldLabel());
            el.setAttribute("name", "./" + field.getFieldName());
            el.setAttribute("multiple", "{Boolean}true");
            el.setAttribute("rootPath", "/content/cq:tags");
            parent.appendChild(el);
        }

        else  if ("multifield".equalsIgnoreCase(field.getFieldType())) {
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
                    if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                        Element items3 = doc.createElement("items");
                        items.setAttribute("jcr:primaryType", "nt:unstructured");
                        for (int i = 0; i < field.getOptions().size(); i++) {
                            OptionItem opt = field.getOptions().get(i);
                            Element option = doc.createElement("option" + (i + 1));
                            option.setAttribute("jcr:primaryType", "nt:unstructured");
                            option.setAttribute("text", opt.getText());
                            option.setAttribute("value", opt.getValue());
                            items3.appendChild(option);
                        }
                        multifield.appendChild(items);
                    }
                }
            }

        }

        else if("multiselect".equalsIgnoreCase(field.getFieldType())){
            Element el = doc.createElement(field.getFieldName());
            el.setAttribute("jcr:primaryType", "nt:unstructured");

            el.setAttribute("sling:resourceType", getResourceType(field.getFieldType()));
            el.setAttribute("fieldLabel", field.getFieldLabel());
            el.setAttribute("name", "./" + field.getFieldName());
            el.setAttribute("multiple", "{Boolean}true");
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
        String currentType = existing.getAttribute("sling:resourceType");
        String newType = getResourceType(field.getFieldType());

        if (!currentType.equals(newType)) {
            clearOldFieldContent(existing); // clean everything!
        }

        existing.setAttribute("jcr:primaryType", "nt:unstructured");
        existing.setAttribute("fieldLabel", field.getFieldLabel());

        log.info("fieldTYpe..................,{}",field.getFieldType());
        if (!field.getFieldType().equals("fileupload"))
        {
            existing.setAttribute("name", "./" + field.getFieldName());
        }
        else {
            existing.setAttribute("name", "./file" );
        }
        existing.setAttribute("sling:resourceType", newType);

        handleTypeSpecificAttributes(existing, field);
    }


    private void handleTypeSpecificAttributes(Element existing, ComponentField field) {
        Document doc = existing.getOwnerDocument();
        String type = field.getFieldType().toLowerCase();

        switch (type) {
            case "richtext":
                existing.setAttribute("useFixedInlineToolbar", "true");
                existing.setAttribute("enableSourceEdit", "true");
                break;

            case "checkbox":
                existing.setAttribute("text", field.getFieldName());
                existing.setAttribute("value", "true");
                existing.setAttribute("uncheckedValue", "false");
                break;

            case "fileupload":
                existing.setAttribute("fileReferenceParameter", "./" + field.getFieldName());
                existing.setAttribute("class", "cq-droptarget");
                existing.setAttribute("mimeTypes", "[image/gif,image/jpeg,image/png,image/tiff,image/svg+xml]");
                existing.setAttribute("multiple", "{Boolean}false");
                existing.setAttribute("uploadUrl", "/content/dam");
                existing.setAttribute("fileNameParameter", "./" + field.getFieldName());
                existing.setAttribute("autoStart", "{Boolean}false");
                break;

            case "tagfield":
                existing.setAttribute("multiple", "{Boolean}true");
                existing.setAttribute("rootPath", "/content/cq:tags");
                break;

            case "select":
                existing.setAttribute("emptyText", "Select...");
                existing.removeAttribute("multiple");

                // Find or create <items>
                Element items1 = findOrCreateItems(existing, doc);
                clearChildElements(items1);

                if (field.getOptions() != null) {
                    for (int i = 0; i < field.getOptions().size(); i++) {
                        OptionItem opt = field.getOptions().get(i);
                        Element option = doc.createElement("option" + (i + 1));
                        option.setAttribute("jcr:primaryType", "nt:unstructured");
                        option.setAttribute("text", opt.getText());
                        option.setAttribute("value", opt.getValue());
                        items1.appendChild(option);
                    }
                }
                break;

            case "multiselect":
                existing.setAttribute("multiple", "{Boolean}true");

                // Find or create <items>
                Element items = findOrCreateItems(existing, doc);
                clearChildElements(items);

                if (field.getOptions() != null) {
                    for (int i = 0; i < field.getOptions().size(); i++) {
                        OptionItem opt = field.getOptions().get(i);
                        Element option = doc.createElement("option" + (i + 1));
                        option.setAttribute("jcr:primaryType", "nt:unstructured");
                        option.setAttribute("text", opt.getText());
                        option.setAttribute("value", opt.getValue());
                        items.appendChild(option);
                    }
                }
                break;

            case "multifield":
                Element fieldNode = doc.createElement("field");
                fieldNode.setAttribute("jcr:primaryType", "nt:unstructured");
                fieldNode.setAttribute("sling:resourceType",
                        "granite/ui/components/coral/foundation/form/fieldset");
                existing.appendChild(fieldNode);

                Element items2 = doc.createElement("items");
                items2.setAttribute("jcr:primaryType", "nt:unstructured");
                fieldNode.appendChild(items2);

                if (field.getNestedFields() != null) {
                    for (ComponentField nested : field.getNestedFields()) {
                        insertField(items2, nested);
                    }
                }
                break;

            // Handle other field types here if necessary

            default:
                // For fields like textfield, numberfield, etc.
                break;
        }
    }

    /** Utility to find or create <items> element */
    private Element findOrCreateItems(Element parent, Document doc) {
        NodeList children = parent.getElementsByTagName("items");
        if (children.getLength() > 0) {
            return (Element) children.item(0);
        } else {
            Element items = doc.createElement("items");
            items.setAttribute("jcr:primaryType", "nt:unstructured");
            parent.appendChild(items);
            return items;
        }
    }

    /** Utility to clear all child elements */
    private void clearChildElements(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                element.removeChild(child);
            }
        }
    }
    private void clearTypeSpecificAttributes(Element existing) {
        // Remove known attributes
        String[] attrs = {
                "fileReferenceParameter", "class", "mimeTypes", "multiple",
                "uploadUrl", "fileNameParameter", "autoStart",
                "text", "value", "uncheckedValue",
                "useFixedInlineToolbar", "enableSourceEdit",
                "emptyText", "namespaces", "rootPath"
        };
        for (String attr : attrs) {
            existing.removeAttribute(attr);
        }
    }

    private void clearOldFieldContent(Element existing) {
        // 1️⃣ Remove all type-specific attributes
        clearTypeSpecificAttributes(existing);

        // 2️⃣ Remove all child elements regardless of type or name
        NodeList children = existing.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                existing.removeChild(child);
            }
        }
    }


    private void clearOldFieldContent(Element existing, String newType) {
        // Remove old type-specific attributes
        clearTypeSpecificAttributes(existing);

        // Determine which child nodes to remove based on type change
        NodeList children = existing.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;

                // Remove all old children if the type is changing away from multifield
                if ("multifield".equalsIgnoreCase(el.getAttribute("sling:resourceType")) ||
                        "fieldset".equalsIgnoreCase(el.getNodeName()) ||
                        "items".equalsIgnoreCase(el.getNodeName()) ||
                        el.getNodeName().startsWith("option")) {
                    existing.removeChild(child);
                }
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
            case "tagfield"     -> "cq/gui/components/coral/common/form/tagfield";
            case "richtext"     -> "cq/gui/components/authoring/dialog/richtext";
            case "switch"       -> "granite/ui/components/coral/foundation/form/switch";
            case "colorfield"   -> "granite/ui/components/coral/foundation/form/colorfield";
            case "fileupload", "image" -> "cq/gui/components/authoring/dialog/fileupload";
            case "select", "multiselect" -> "granite/ui/components/coral/foundation/form/select";
            case "password"    -> "granite/ui/components/coral/foundation/form/password";
            case "checkbox"    -> "granite/ui/components/coral/foundation/form/checkbox";
            case "tabs"        -> "granite/ui/components/coral/foundation/tabs";
            case "radiogroup"  ->"granite/ui/components/coral/foundation/form/radiogroup";
            case "multifield" ->"granite/ui/components/coral/foundation/form/multifield";
            default -> "granite/ui/components/coral/foundation/form/textfield"; // fallback
        };
    }


      @Override
    public  void updateComponentGroup(File xmlFile, String newGroup) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            Element root = doc.getDocumentElement();

            String currentGroup = root.getAttribute("componentGroup");

            if (!newGroup.equals(currentGroup)) {
                root.setAttribute("componentGroup", newGroup);

                // Write changes back to file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(xmlFile);
                transformer.transform(source, result);

                System.out.println("Component group updated successfully.");
            } else {
                System.out.println("Component group is already up to date.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


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
            throw new RuntimeException("No Sling Model found in HTL: " + htlPath);
        }

        // 3. Locate Sling Model .java file
        Path javaFilePath = locateJavaFile(request.getProjectName(), slingModelClass);
        if (javaFilePath == null) {
            throw new RuntimeException("Could not find Java file for model: " + slingModelClass);
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

        // Replace field declaration
        content = content.replaceAll(
                "(?s)@(?:ValueMapValue|ChildResource)\\s+private[^{;]+\\s+" + fieldName + "\\s*;",
                annotation + " private " + type + " " + fieldName + ";"
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
                    "@Model(adaptables = Resource.class, defaultInjectionStrategy " +
                    "= DefaultInjectionStrategy.OPTIONAL)\n" +
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
            case "tagfield","multiselect" -> "List<String>";

            default -> "String"; // fallback
        };
    }


    // Map field type to Java type
    private String mapFieldType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "numberfield" -> "double";
            case "textfield", "textarea", "password", "pathfield" -> "String";
            case "tagfield","multiselect" -> "List<String>";
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


}