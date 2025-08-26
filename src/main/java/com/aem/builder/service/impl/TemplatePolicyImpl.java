package com.aem.builder.service.impl;

import com.aem.builder.model.PolicyRequest;
import com.aem.builder.service.TemplatePolicy;
import com.aem.builder.util.TemplateUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TemplatePolicyImpl implements TemplatePolicy {

    @Override
    public String addPolicy(String projectname, String policyName, String componentGroups,
                            String styleDefaultClasses, String styleDefaultElement,
                            Map<String, Map<String, Object>> styles) throws Exception {

        String POLICIES_PATH =
                "generated-projects/" + projectname +
                        "/ui.content/src/main/content/jcr_root/conf/" + projectname +
                        "/settings/wcm/policies/.content.xml";

        File xmlFile = new File(POLICIES_PATH);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Node containerNode = getOrCreateContainerNode(doc);

        // Create policy node
        String policyNodeName = "policy_" + System.currentTimeMillis();
        Element policy = doc.createElement(policyNodeName);

        policy.setAttribute("cq:styleDefaultClasses", styleDefaultClasses);
        policy.setAttribute("cq:styleDefaultElement", styleDefaultElement);
        String jcrDate = "{Date}" + ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));

        policy.setAttribute("jcr:lastModified", jcrDate);
        policy.setAttribute("jcr:lastModifiedBy", "admin");
        policy.setAttribute("jcr:primaryType", "nt:unstructured");
        policy.setAttribute("jcr:title", policyName);
        policy.setAttribute("sling:resourceType", "wcm/core/components/policy/policy");
        policy.setAttribute("components", componentGroups);
        policy.setAttribute("layoutDisabled", "false");

        // jcr:content
        Element jcrContent = doc.createElement("jcr:content");
        jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
        policy.appendChild(jcrContent);

        // add styleGroups
        appendStyleGroups(doc, policy, styles);

        containerNode.appendChild(policy);

        // Save XML back
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));

        return policy.getNodeName();
    }

    public String updatePolicy(String policyname) {
        return policyname;
    }

    private Node getOrCreateContainerNode(Document doc) {
        NodeList containerNodes = doc.getElementsByTagName("container");
        if (containerNodes.getLength() > 0) {
            return containerNodes.item(1);
        }
        Element container = doc.createElement("container");
        container.setAttribute("jcr:primaryType", "nt:unstructured");
        doc.getDocumentElement()
                .getElementsByTagName("components")
                .item(0)
                .appendChild(container);
        return container;
    }

    @Override
    public void assignPolicyToTemplate(String projectName, String templateName,
                                       String policyNodeName) throws Exception {
        String templatePath = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/templates/" + templateName + "/policies/.content.xml";
        String templateType = getTemplateType(projectName, templateName);
        System.out.println("Template Type for " + templateName + ": " + templateType);

        File xmlFile = new File(templatePath);
        try {
            TemplateUtil.updatePolicyId(templatePath, policyNodeName,projectName,templateType);
        } catch (Exception e) {
            throw new IOException("something happend");
        }
    }

    void writeFile(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
    }

    public void saveOrUpdatePolicy(String projectName, String templateName,
                                   PolicyRequest request) throws Exception {

        String POLICIES_PATH =
                "generated-projects/" + projectName +
                        "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                        "/settings/wcm/policies/.content.xml";

        File xmlFile = new File(POLICIES_PATH);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Node containerNode = getOrCreateContainerNode(doc);
        NodeList policyNodes = containerNode.getChildNodes();
        boolean updated = false;

        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element policyEl = (Element) node;

                if (policyEl.hasAttribute("jcr:title") &&
                        policyEl.getAttribute("jcr:title").equals(request.getName())) {

                    // Clear all children
                    NodeList children = policyEl.getChildNodes();
                    for (int j = children.getLength() - 1; j >= 0; j--) {
                        policyEl.removeChild(children.item(j));
                    }

                    // overwrite attributes
                    policyEl.setAttribute("jcr:title", request.getName());
                    policyEl.setAttribute("sling:resourceType", "wcm/core/components/policy/policy");
                    policyEl.setAttribute("components", request.getComponentPath());
                    policyEl.setAttribute("layoutDisabled", "false");
                    String jcrDate = "{Date}" + ZonedDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
                    policyEl.setAttribute("jcr:lastModified", jcrDate);
                    policyEl.setAttribute("jcr:lastModifiedBy", "admin");
                    policyEl.setAttribute("cq:styleDefaultClasses", request.getStyleDefaultClasses());
                    policyEl.setAttribute("cq:styleDefaultElement", request.getStyleDefaultElement());

                    // jcr:content
                    Element jcrContent = doc.createElement("jcr:content");
                    jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
                    policyEl.appendChild(jcrContent);

                    // styleGroups
                    appendStyleGroups(doc, policyEl, request.getStyles());

                    updated = true;
                    String policynode = policyEl.getNodeName();
                    assignPolicyToTemplate(projectName, templateName, policynode);
                    break;
                }
            }
        }

        if (updated) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
            transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
        } else {
            String policynode = addPolicy(
                    projectName,
                    request.getName(),
                    request.getComponentPath(),
                    request.getStyleDefaultClasses(),
                    request.getStyleDefaultElement(),
                    request.getStyles()
            );
            assignPolicyToTemplate(projectName, templateName, policynode);
        }
    }

    @Override
    public List<String> getExistingPolicies(String projectName) throws Exception {
        List<String> policies = new ArrayList<>();
        String path = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/policies/.content.xml";
        File file = new File(path);

        if (!file.exists()) return policies;

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(file);
        NodeList nodeList = doc.getElementsByTagName("container").item(1).getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.hasAttribute("jcr:title")) {
                    policies.add(element.getAttribute("jcr:title"));
                }
            }
        }
        return policies;
    }

    @Override
    public PolicyRequest getPolicyDetails(String projectName, String policyTitle) throws Exception {
        String path = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/policies/.content.xml";

        File file = new File(path);
        if (!file.exists()) return null;

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(file);

        NodeList containers = doc.getElementsByTagName("container");
        if (containers.getLength() < 2) return null;
        Node container = containers.item(1);

        NodeList policies = container.getChildNodes();
        for (int i = 0; i < policies.getLength(); i++) {
            Node node = policies.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element policyEl = (Element) node;
                if (policyTitle.equals(policyEl.getAttribute("jcr:title"))) {

                    PolicyRequest request = new PolicyRequest();
                    request.setName(policyEl.getAttribute("jcr:title"));
                    request.setComponentPath(policyEl.getAttribute("components"));
                    request.setStyleDefaultClasses(policyEl.getAttribute("cq:styleDefaultClasses"));
                    request.setStyleDefaultElement(policyEl.getAttribute("cq:styleDefaultElement"));

                    // Parse styles
                    Map<String, Map<String, Object>> styleGroups = new LinkedHashMap<>();
                    NodeList styleGroupsNode = policyEl.getElementsByTagName("cq:styleGroups");
                    if (styleGroupsNode.getLength() > 0) {
                        NodeList groups = styleGroupsNode.item(0).getChildNodes();
                        for (int g = 0; g < groups.getLength(); g++) {
                            Node groupNode = groups.item(g);
                            if (groupNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element groupEl = (Element) groupNode;
                                String groupName = groupEl.getAttribute("cq:styleGroupLabel");

                                Map<String, Object> groupData = new LinkedHashMap<>();
                                groupData.put("multiple", "true".equals(groupEl.getAttribute("cq:styleGroupMultiple")));

                                Map<String, Object> items = new LinkedHashMap<>();
                                NodeList stylesNodes = groupEl.getElementsByTagName("cq:styles");
                                if (stylesNodes.getLength() > 0) {
                                    NodeList styleItems = stylesNodes.item(0).getChildNodes();
                                    for (int s = 0; s < styleItems.getLength(); s++) {
                                        Node styleNode = styleItems.item(s);
                                        if (styleNode.getNodeType() == Node.ELEMENT_NODE) {
                                            Element styleEl = (Element) styleNode;
                                            String label = styleEl.getAttribute("cq:styleLabel");
                                            String cssClass = styleEl.getAttribute("cq:styleClasses");
                                            String element = styleEl.getAttribute("cq:styleElement");

                                            Map<String, String> def = new HashMap<>();
                                            def.put("cls", cssClass);
                                            System.out.println(cssClass);
                                            def.put("element", element);
                                            System.out.println(element);
                                            System.out.println(label);
                                            items.put(label, def);
                                        }
                                    }
                                }
                                groupData.put("items", items);
                                styleGroups.put(groupName, groupData);
                            }
                        }
                    }

                    request.setStyles(styleGroups);
                    return request;
                }
            }
        }

        return null;
    }



    /**
     * Appends style groups and their styles to the policy XML element.
     */
    private void appendStyleGroups(Document doc, Element policyEl,
                                   Map<String, Map<String, Object>> styles) {
        if (styles == null || styles.isEmpty()) return;

        Element styleGroups = doc.createElement("cq:styleGroups");
        styleGroups.setAttribute("jcr:primaryType", "nt:unstructured");

        int groupIndex = 0;
        for (Map.Entry<String, Map<String, Object>> groupEntry : styles.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, Object> groupData = groupEntry.getValue();

            // ✅ Items: Map<label, {class, element}>
            @SuppressWarnings("unchecked")
            Map<String, Object> styleItems =
                    (Map<String, Object>) groupData.getOrDefault("items", new LinkedHashMap<>());

            // ✅ Handle "multiple" as Boolean or String
            boolean isMultiple = false;
            if (groupData.containsKey("multiple")) {
                Object multipleVal = groupData.get("multiple");
                if (multipleVal instanceof Boolean) {
                    isMultiple = (Boolean) multipleVal;
                } else if (multipleVal instanceof String) {
                    isMultiple = Boolean.parseBoolean((String) multipleVal);
                }
            }

            Element styleGroup = doc.createElement("item" + groupIndex++);
            styleGroup.setAttribute("jcr:primaryType", "nt:unstructured");
            styleGroup.setAttribute("cq:styleGroupLabel", groupName);

            if (isMultiple) {
                styleGroup.setAttribute("cq:styleGroupMultiple", "true");
            }

            Element cqStyles = doc.createElement("cq:styles");
            cqStyles.setAttribute("jcr:primaryType", "nt:unstructured");

            int iStyle = 0;
            for (Map.Entry<String, Object> styleEntry : styleItems.entrySet()) {
                String label = styleEntry.getKey();

                // Each item is expected to be a Map { class, element }
                @SuppressWarnings("unchecked")
                Map<String, String> styleDef = (Map<String, String>) styleEntry.getValue();

                String cssClass = styleDef.getOrDefault("class", "");
                String element = styleDef.getOrDefault("element", "div");

                Element styleItem = doc.createElement("item" + iStyle++);
                styleItem.setAttribute("jcr:primaryType", "nt:unstructured");
                styleItem.setAttribute("cq:styleLabel", label);
                styleItem.setAttribute("cq:styleClasses", cssClass);
                styleItem.setAttribute("cq:styleElement", element);
                styleItem.setAttribute("cq:styleId", String.valueOf(System.currentTimeMillis() + iStyle));

                cqStyles.appendChild(styleItem);
            }

            styleGroup.appendChild(cqStyles);
            styleGroups.appendChild(styleGroup);
        }

        policyEl.appendChild(styleGroups);
    }

    private String getTemplateType(String projectName, String templateName) throws Exception {
        String templatePath = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/templates/" + templateName + "/.content.xml";

        File xmlFile = new File(templatePath);
        if (!xmlFile.exists()) {
            throw new IOException("Template .content.xml not found: " + templatePath);
        }

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Element root = doc.getDocumentElement();

        // 🔎 Look for <jcr:content> element
        NodeList contentNodes = root.getElementsByTagName("jcr:content");
        if (contentNodes.getLength() > 0) {
            Element content = (Element) contentNodes.item(0);
            String templateType = content.getAttribute("cq:templateType");
            if (templateType != null && !templateType.isEmpty()) {
                String[] parts = templateType.split("/");
                return parts[parts.length - 1]; // returns "page"
            }

        }

        return ""; // No templateType → static template
    }



}