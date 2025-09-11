package com.aem.builder.util;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.model.Enum.FieldType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class to incrementally update AEM component files: HTL, dialog XML, and Sling model
 */
public class ComponentUpdateUtil {

    private static final Logger log = LoggerFactory.getLogger(ComponentUpdateUtil.class);

    /**
     * Updates all files of a component based on new ComponentRequest
     */
    public static void updateComponentFiles(String componentFolderPath, String modelBasePath, String packageName, ComponentRequest request) throws Exception {
        String componentName = request.getComponentName();

        // Update HTL
        Path htlFile = Path.of(componentFolderPath, componentName + ".html");
        updateHTLFile(htlFile.toFile(), packageName, componentName, request.getFields());

        // Update dialog .content.xml
        Path dialogFile = Path.of(componentFolderPath, "_cq_dialog", ".content.xml");
        updateDialogFile(dialogFile.toFile(), request);

        // Update Sling Model
        Path modelFile = Path.of(modelBasePath, capitalize(componentName) + "Model.java");
        updateSlingModelFile(modelFile.toFile(), packageName, componentName, request.getFields(), modelBasePath);
    }

    // -------------------- HTL UPDATE --------------------

    private static void updateHTLFile(File htlFile, String packageName, String componentName, List<ComponentField> fields) throws Exception {
        log.info("Updating HTL file: {}", htlFile.getAbsolutePath());

        String content = FileUtils.readFileToString(htlFile, StandardCharsets.UTF_8);

        // Use markers to detect fields
        StringBuilder sb = new StringBuilder();

        sb.append("<sly data-sly-use.model=\"").append(packageName).append(".").append(capitalize(componentName)).append("Model\"/>\n");
        sb.append("<sly data-sly-use.placeholderTemplate=\"core/wcm/components/commons/v1/templates.html\"/>\n");
        sb.append("<sly data-sly-test.hasContent=\"${!model.empty}\">\n");

        for (ComponentField field : fields) {
            appendHTLForField(sb, field, "model", "  ");
        }

        sb.append("</sly>\n");
        sb.append("<sly data-sly-call=\"${placeholderTemplate.placeholder @ isEmpty = !hasContent}\" />\n");

        FileUtils.writeStringToFile(htlFile, sb.toString(), StandardCharsets.UTF_8);
        log.info("HTL updated: {}", htlFile.getAbsolutePath());
    }

    private static void appendHTLForField(StringBuilder sb, ComponentField field, String modelVar, String indent) {
        String type = field.getFieldType().toLowerCase();

    Object.keys(data).forEach((group) => {
      const item = document.createElement("div");
      item.className = "accordion-item";

      const header = document.createElement("div");
      header.className = "accordion-header";
      header.textContent = group;
      header.addEventListener("click", () =>
        item.classList.toggle("active")
      );

      const content = document.createElement("div");
      content.className = "accordion-content";

      const selectAll = document.createElement("label");
      selectAll.innerHTML = `<input type="checkbox" class="select-all" data-group="${group}"/> Select All`;
      content.appendChild(selectAll);

      data[group].forEach((comp) => {
        const label = document.createElement("label");
        label.innerHTML = `<input type="checkbox" value="${comp.path}"/> ${comp.name}`;
        content.appendChild(document.createElement("br"));
        content.appendChild(label);
      });

      item.appendChild(header);
      item.appendChild(content);
      accordionContainer.appendChild(item);
    });

    // Select all toggle
    accordionContainer.addEventListener("change", (e) => {
      if (e.target.classList.contains("select-all")) {
        const group = e.target.dataset.group;
        const checkboxes = accordionContainer.querySelectorAll(
          ".accordion-content input[type=checkbox]:not(.select-all)"
        );
        checkboxes.forEach((cb) => {
          if (
            cb
              .closest(".accordion-item")
              .querySelector(".accordion-header").textContent === group
          ) {
            cb.checked = e.target.checked;
          }
        });
      }
      updateComponentPath();
    });
  }

  // Update selected paths
 // Update selected groups instead of component paths
 function updateComponentPath() {
   const selectedItems = [];

   document.querySelectorAll(".accordion-item").forEach((item) => {
     const groupName = item.querySelector(".accordion-header").textContent.trim();
     const selectAll = item.querySelector('.select-all');
     const checkboxes = item.querySelectorAll(
       '.accordion-content input[type="checkbox"]:not(.select-all)'
     );

     if (selectAll && selectAll.checked) {
       // Case 1: Select All checked → store group
       selectedItems.push(`group:${groupName}`);
     } else {
       // Case 2: Some individual items checked → store their values
       checkboxes.forEach((cb) => {
         if (cb.checked) {
           selectedItems.push(cb.value);
         }
       });
     }
   });

   document.getElementById("componentPathOutput").textContent =
     `[${selectedItems.join(",")}]`;
 }



  // Add Style Group
  function addStyleGroup() {
    const container = document.getElementById("styleGroups");
    const groupDiv = document.createElement("div");
    groupDiv.className = "style-group";
    groupDiv.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <input type="text" class="group-name" placeholder="Style Group Name" required>
         <label><input type="checkbox" class="group-checkbox"> styles can be combined</label>
        <button type="button" class="remove-btn" onclick="this.closest('.style-group').remove()">❌ Remove Group</button>
      </div>

      <div class="styles"></div>
            <button type="button" class="add-btn" onclick="addStyleRow(this)">+ Add Style</button>
    `;
    container.appendChild(groupDiv);
  }

  // Add Style Row
  function addStyleRow(btn, style = { label: "", cls: "", element: "div" }) {
    const stylesDiv = btn.parentElement.querySelector(".styles");
    const row = document.createElement("div");
    row.className = "style-row";
    row.innerHTML = `
      <input type="text" placeholder="Style Label" class="style-label" value="${style.label}" required>
      <input type="text" placeholder="CSS Class" class="style-class" value="${style.cls}" required>
      <select class="style-element" required>
        <option value="">Element --</option>
        <option value="div" ${style.element === "div" ? "selected" : ""}>div</option>
        <option value="section" ${style.element === "section" ? "selected" : ""}>section</option>
        <option value="article" ${style.element === "article" ? "selected" : ""}>article</option>
        <option value="main" ${style.element === "main" ? "selected" : ""}>main</option>
        <option value="aside" ${style.element === "aside" ? "selected" : ""}>aside</option>
        <option value="header" ${style.element === "header" ? "selected" : ""}>header</option>
        <option value="footer" ${style.element === "footer" ? "selected" : ""}>footer</option>
      </select>
      <button type="button" class="remove-btn" onclick="this.parentElement.remove()">❌</button>
    `;
    stylesDiv.appendChild(row);
  }

  // Fetch and fill policy details
  async function fetchAndFillPolicyDetails(projectName, policyTitle) {
    const response = await fetch(
      `/get-policy-details?projectName=${projectName}&policyTitle=${encodeURIComponent(
        policyTitle
      )}`
    );
    if (!response.ok) return;

    const data = await response.json();
    console.log(data);

    document.getElementById("newPolicyTitle").value = data.name || "";
    document.getElementById("componentPathOutput").textContent =
      data.componentPath || "";
    document.getElementById("styleDefaultClasses").value =
      data.styleDefaultClasses || "";
    document.getElementById("styleDefaultElement").value =
      data.styleDefaultElement || "";

    // Component paths
   const paths = data.componentPath
     ? data.componentPath.replace(/[\[\]]/g, "").split(",")
     : [];

   document.querySelectorAll(".accordion-item").forEach((item) => {
     const groupName = `group:${item.querySelector(".accordion-header").textContent.trim()}`;
     const selectAll = item.querySelector(".select-all");
     const checkboxes = item.querySelectorAll(
       '.accordion-content input[type=checkbox]:not(.select-all)'
     );

     if (paths.includes(groupName)) {
       // Case 1: group saved → check "Select All"
       if (selectAll) selectAll.checked = true;
       checkboxes.forEach((cb) => (cb.checked = true));
     } else {
       // Case 2: some individual paths saved → match them
       checkboxes.forEach((cb) => {
         cb.checked = paths.includes(cb.value);
       });
       if (selectAll) {
         selectAll.checked = Array.from(checkboxes).every((cb) => cb.checked);
       }
     }
   });
   updateComponentPath();



    // Style groups
    const container = document.getElementById("styleGroups");
    container.innerHTML = "";

    if (data.styles) {
      Object.entries(data.styles).forEach(([groupName, groupObj]) => {
        const groupDiv = document.createElement("div");
        groupDiv.className = "style-group";
        groupDiv.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <input type="text" class="group-name" value="${groupName}" required>
             <label><input type="checkbox" class="group-checkbox" ${
                        groupObj.multiple ? "checked" : ""
                      }> styles can be combined</label>
            <button type="button" class="remove-btn" onclick="this.closest('.style-group').remove()">❌ Remove Group</button>
          </div>

          <button type="button" class="add-btn" onclick="addStyleRow(this)">+ Add Style</button>
          <div class="styles"></div>
        `;

        const stylesDiv = groupDiv.querySelector(".styles");


Object.entries(groupObj.items).forEach(([label, def]) => {
  addStyleRow(
    { parentElement: groupDiv },
    { label, cls: def.cls || "", element: def.element || "div" }
  );
});

        container.appendChild(groupDiv);
      });
    }
  }

  // Submit Policy
  async function submitPolicy() {
    const form = document.getElementById("policyForm");

    const styleGroups = {};
    document.querySelectorAll(".style-group").forEach((group) => {
      const groupName = group.querySelector(".group-name").value.trim();
      if (!groupName) return;

      const styles = {};
     group.querySelectorAll(".style-row").forEach((row) => {
       const label = row.querySelector(".style-label").value.trim();
       const cls = row.querySelector(".style-class").value.trim();
       const element = row.querySelector(".style-element").value.trim();
       if (label && cls) styles[label] = { class: cls, element };
     });


      styleGroups[groupName] = {
        multiple: group.querySelector(".group-checkbox").checked,
        items: styles,
      };
    });

    const componentPath = document.getElementById(
      "componentPathOutput"
    ).textContent;
    const newPolicyTitleInput =
      document.getElementById("newPolicyTitle").value;

    const data = {
      projectName,
      name: newPolicyTitleInput,
      styleDefaultClasses: form.styleDefaultClasses.value,
      styleDefaultElement: form.styleDefaultElement.value,
      componentPath,
      styles: styleGroups,
    };

    try {
      spinnerOverlay.classList.remove("d-none");
      const spinnerStart = Date.now();

      await fetch(
        `/policies/add/${projectName}?templateName=${encodeURIComponent(
          templateName
        )}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(data),
        }

        String fieldName = field.getFieldName();
        String fieldLabel = field.getFieldLabel();

        sb.append(indent).append("<sly data-sly-test=\"${").append(modelVar).append(".").append(fieldName).append("}\">\n");

        switch (type) {
            case "checkbox" -> sb.append(indent).append("  <p>").append(fieldLabel).append(": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n");
            case "image", "fileupload" -> sb.append(indent).append("  <p>").append(fieldLabel).append(": <img src=\"${").append(modelVar).append(".").append(fieldName).append("}\" alt=\"").append(fieldLabel).append("\" style=\"max-width:100%; height:auto;\"/></p>\n");
            case "pathfield" -> sb.append(indent).append("  <p>").append(fieldLabel).append(": <a href=\"${").append(modelVar).append(".").append(fieldName).append("}\">${").append(modelVar).append(".").append(fieldName).append("}</a></p>\n");
            case "richtext" -> sb.append(indent).append("  <p>").append(fieldLabel).append(": ${").append(modelVar).append(".").append(fieldName).append(" @ context='html'}</p>\n");
            case "multifield" -> {
                sb.append(indent).append("  <ul data-sly-list.item=\"${").append(modelVar).append(".").append(fieldName).append("}\">\n");
                if (field.getNestedFields() != null) {
                    for (ComponentField nf : field.getNestedFields()) {
                        appendHTLForField(sb, nf, "item", indent + "    ");
                    }
                }
                sb.append(indent).append("  </ul>\n");
            }
            case "multiselect", "tagfield" -> sb.append(indent).append("  <ul data-sly-list.item=\"${").append(modelVar).append(".").append(fieldName).append("}\"><li>${item}</li></ul>\n");
            default -> sb.append(indent).append("  <p>").append(fieldLabel).append(": ${").append(modelVar).append(".").append(fieldName).append("}</p>\n");
        }

        sb.append(indent).append("</sly>\n");
    }

    // -------------------- DIALOG XML UPDATE --------------------

    private static void updateDialogFile(File dialogFile, ComponentRequest request) throws Exception {
        log.info("Updating dialog file: {}", dialogFile.getAbsolutePath());

        if (!dialogFile.exists()) {
            dialogFile.getParentFile().mkdirs();
            dialogFile.createNewFile();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc;

        if (dialogFile.length() == 0) {
            doc = builder.newDocument();
            Element root = doc.createElement("jcr:root");
            root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
            root.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
            root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
            root.setAttribute("jcr:primaryType", "nt:unstructured");
            root.setAttribute("jcr:title", request.getComponentName() + " Dialog");
            root.setAttribute("sling:resourceType", "cq/gui/components/authoring/dialog");
            doc.appendChild(root);
        } else {
            doc = builder.parse(dialogFile);
        }

        Element root = doc.getDocumentElement();
        Element content = findOrCreateChild(doc, root, "content", "granite/ui/components/coral/foundation/container");

        Element items = findOrCreateChild(doc, content, "items", "nt:unstructured");

        // Build a map of existing fields by name
        Map<String, Element> existingFields = new HashMap<>();
        NodeList nodeList = items.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            if (n instanceof Element el) {
                String nameAttr = el.getAttribute("name");
                if (nameAttr != null && !nameAttr.isEmpty()) existingFields.put(nameAttr, el);
            }
        }

        Set<String> requestedNames = request.getFields().stream().map(f -> "./" + f.getFieldName()).collect(Collectors.toSet());

        // Remove fields no longer needed
        for (Element e : existingFields.values()) {
            if (!requestedNames.contains(e.getAttribute("name"))) {
                items.removeChild(e);
            }
        }

        // Add or update fields
        for (ComponentField f : request.getFields()) {
            String nodeName = "./" + f.getFieldName();
            if (existingFields.containsKey(nodeName)) {
                updateFieldAttributes(existingFields.get(nodeName), f);
            } else {
                Element newField = createFieldElement(doc, f);
                items.appendChild(newField);
            }
        }

        // Save XML
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(dialogFile));

        log.info("Dialog updated: {}", dialogFile.getAbsolutePath());
    }

    private static Element findOrCreateChild(Document doc, Element parent, String name, String resourceType) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && name.equals(el.getNodeName())) return el;
        }
        Element child = doc.createElement(name);
        child.setAttribute("jcr:primaryType", "nt:unstructured");
        child.setAttribute("sling:resourceType", resourceType);
        parent.appendChild(child);
        return child;
    }

    private static void updateFieldAttributes(Element el, ComponentField field) {
        el.setAttribute("fieldLabel", field.getFieldLabel());
        el.setAttribute("sling:resourceType", ComponentUpdateUtil.getResourceType(field.getFieldType()));
    }

    private static Element createFieldElement(Document doc, ComponentField field) {
        Element el = doc.createElement(field.getFieldName());
        el.setAttribute("jcr:primaryType", "nt:unstructured");
        el.setAttribute("sling:resourceType", ComponentUpdateUtil.getResourceType(field.getFieldType()));
        el.setAttribute("fieldLabel", field.getFieldLabel());
        return el;
    }

    // -------------------- SLING MODEL UPDATE --------------------

    private static void updateSlingModelFile(File modelFile, String packageName, String componentName, List<ComponentField> fields, String modelBasePath) throws Exception {
        log.info("Updating Sling Model: {}", modelFile.getAbsolutePath());

        List<String> lines = modelFile.exists() ? FileUtils.readLines(modelFile, StandardCharsets.UTF_8) : new ArrayList<>();
        String className = capitalize(componentName) + "Model";

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

        // Add fields recursively
        List<ComponentField> generatedFields = addFieldsToModel(sb, modelBasePath, packageName, componentName, fields);

        // Add isEmpty() method
        sb.append("    public boolean isEmpty() {\n        boolean empty = true;\n");
        for (ComponentField f : generatedFields) {
            String name = f.getFieldName();
            String type = f.getFieldType().toLowerCase();
            switch (type) {
                case "multifield", "multiselect", "tagfield" -> sb.append("        if (").append(name).append(" != null && !").append(name).append(".isEmpty()) empty = false;\n");
                case "checkbox" -> sb.append("        if (").append(name).append(") empty = false;\n");
                case "numberfield" -> sb.append("        if (").append(name).append(" != 0) empty = false;\n");
                default -> sb.append("        if (").append(name).append(" != null && !").append(name).append(".isEmpty()) empty = false;\n");
            }
        }
        sb.append("        return empty;\n    }\n");
        sb.append("}");

        FileUtils.writeStringToFile(modelFile, sb.toString(), StandardCharsets.UTF_8);
        log.info("Sling Model updated: {}", modelFile.getAbsolutePath());
    }

    private static List<ComponentField> addFieldsToModel(StringBuilder sb, String modelBasePath, String packageName, String component
