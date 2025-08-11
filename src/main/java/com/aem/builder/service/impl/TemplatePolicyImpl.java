package com.aem.builder.service.impl;

import com.aem.builder.model.PolicyRequest;
import com.aem.builder.service.TemplatePolicy;
import com.aem.builder.util.TemplateUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class TemplatePolicyImpl implements TemplatePolicy {

    @Override
    public String addPolicy(String projectname, String policyName, String componentGroups, String styleDefaultClasses,
                            String styleDefaultElement, Map<String, Map<String, String>> styles) throws Exception {


        String POLICIES_PATH =
                "generated-projects/" + projectname + "/ui.content/src/main/content/jcr_root/conf/" + projectname + "/settings/wcm/policies/.content.xml";
        File xmlFile = new File(POLICIES_PATH);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Node containerNode = getOrCreateContainerNode(doc);
        // Create policy node
        String policyNodeName = "policy_" + System.currentTimeMillis();
        Element policy = doc.createElement(policyNodeName);
        System.out.println(policy.getAttributes() + "  is created");

        policy.setAttribute("cq:styleDefaultClasses", styleDefaultClasses);
        policy.setAttribute("cq:styleDefaultElement", styleDefaultElement);
        String jcrDate = "{Date}" + ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));

        policy.setAttribute("jcr:lastModified", jcrDate);
        policy.setAttribute("jcr:lastModifiedBy", "admin");

        policy.setAttribute("jcr:primaryType", "nt:unstructured");
        policy.setAttribute("jcr:title", policyName);
        policy.setAttribute("sling:resourceType", "wcm/core/components/policy/policy");

        // Components as groups
        policy.setAttribute("components", componentGroups);

        policy.setAttribute("layoutDisabled", "false");


        // jcr:content
        Element jcrContent = doc.createElement("jcr:content");
        jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
        policy.appendChild(jcrContent);

        //  Add cq:styleGroups with proper label
        if (styles != null && !styles.isEmpty()) {
            Element styleGroups = doc.createElement("cq:styleGroups");
            styleGroups.setAttribute("jcr:primaryType", "nt:unstructured");

            int groupIndex = 0;
            for (Map.Entry<String, Map<String, String>> groupEntry : styles.entrySet()) {
                String groupName = groupEntry.getKey();
                Map<String, String> styleItems = groupEntry.getValue();

                Element styleGroup = doc.createElement("item" + groupIndex++);
                styleGroup.setAttribute("jcr:primaryType", "nt:unstructured");
                styleGroup.setAttribute("cq:styleGroupLabel", groupName);

                Element cqStyles = doc.createElement("cq:styles");
                cqStyles.setAttribute("jcr:primaryType", "nt:unstructured");

                int i = 0;
                for (Map.Entry<String, String> style : styleItems.entrySet()) {
                    Element styleItem = doc.createElement("item" + i++);
                    styleItem.setAttribute("jcr:primaryType", "nt:unstructured");
                    styleItem.setAttribute("cq:styleClasses", style.getValue());
                    styleItem.setAttribute("cq:styleId", String.valueOf(System.currentTimeMillis() + i));
                    styleItem.setAttribute("cq:styleElement", "div");
                    styleItem.setAttribute("cq:styleLabel", style.getKey());


                    cqStyles.appendChild(styleItem);
                }

                styleGroup.appendChild(cqStyles);
                styleGroups.appendChild(styleGroup);
            }

            policy.appendChild(styleGroups);
        }

        containerNode.appendChild(policy);
        System.out.println("policy...." + policy.getNodeName());

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

        File xmlFile = new File(templatePath);
        try {
            writeFile(templatePath, TemplateUtil.policyForParticularTemplate(policyNodeName, projectName));
        } catch (Exception e) {
            throw new IOException("something happend");
        }

    }


    void writeFile(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        System.out.println("Written file: " + path);
    }


    public void saveOrUpdatePolicy(String projectName, String templateName, PolicyRequest request) throws Exception {
        String POLICIES_PATH =
                "generated-projects/" + projectName + "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/policies/.content.xml";
        File xmlFile = new File(POLICIES_PATH);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Node containerNode = getOrCreateContainerNode(doc); // Reuse your existing method

        NodeList policyNodes = containerNode.getChildNodes();
        boolean updated = false;

        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element policyEl = (Element) node;

                if (policyEl.hasAttribute("jcr:title") && policyEl.getAttribute("jcr:title").equals(request.getName())) {
                    // ✅ Found existing policy by title

                    // Clear all children (e.g., cq:styleGroups, jcr:content etc.)
                    NodeList children = policyEl.getChildNodes();
                    for (int j = children.getLength() - 1; j >= 0; j--) {
                        policyEl.removeChild(children.item(j));
                    }

                    // Overwrite all attributes
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

                    // ✅ Re-create jcr:content (optional, based on your needs)
                    Element jcrContent = doc.createElement("jcr:content");
                    jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
                    policyEl.appendChild(jcrContent);

                    // ✅ Re-create style groups
                    if (request.getStyles() != null && !request.getStyles().isEmpty()) {
                        Element styleGroups = doc.createElement("cq:styleGroups");
                        styleGroups.setAttribute("jcr:primaryType", "nt:unstructured");

                        int groupIndex = 0;
                        for (Map.Entry<String, Map<String, String>> groupEntry : request.getStyles().entrySet()) {
                            String groupName = groupEntry.getKey();
                            Map<String, String> styleItems = groupEntry.getValue();

                            Element styleGroup = doc.createElement("item" + groupIndex++);
                            styleGroup.setAttribute("jcr:primaryType", "nt:unstructured");
                            styleGroup.setAttribute("cq:styleGroupLabel", groupName);

                            Element cqStyles = doc.createElement("cq:styles");
                            cqStyles.setAttribute("jcr:primaryType", "nt:unstructured");

                            int iStyle = 0;
                            for (Map.Entry<String, String> style : styleItems.entrySet()) {
                                Element styleItem = doc.createElement("item" + iStyle++);
                                styleItem.setAttribute("jcr:primaryType", "nt:unstructured");
                                styleItem.setAttribute("cq:styleLabel", style.getKey());
                                styleItem.setAttribute("cq:styleClasses", style.getValue());
                                styleItem.setAttribute("cq:styleElement", "div");
                                styleItem.setAttribute("cq:styleId", String.valueOf(System.currentTimeMillis() + iStyle));
                                cqStyles.appendChild(styleItem);
                            }

                            styleGroup.appendChild(cqStyles);
                            styleGroups.appendChild(styleGroup);
                        }

                        policyEl.appendChild(styleGroups);
                    }

                    updated = true;
                    String policynode = policyEl.getNodeName();
                    assignPolicyToTemplate(projectName, templateName, policynode);
                    System.out.println("✅ Existing policy updated");
                    break;
                }
            }
        }

        if (updated) {
            // Save back only if update was made to the in-memory `doc`
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
        } else {
            // Add new policy (already saved inside addPolicy)
            String policynode = addPolicy(
                    projectName,
                    request.getName(),
                    request.getComponentPath(),
                    request.getStyleDefaultClasses(),
                    request.getStyleDefaultElement(),
                    request.getStyles()
            );
            assignPolicyToTemplate(projectName, templateName, policynode);
            System.out.println("➕ New policy created: " + request.getName());
        }

    }

    @Override
    public List<String> getExistingPolicies(String projectName) throws Exception {
        List<String> policies = new ArrayList<>();
        String path = "generated-projects/" + projectName + "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/policies/.content.xml";
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
        String path = "generated-projects/" + projectName + "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/policies/.content.xml";
        File file = new File(path);

        if (!file.exists()) return null;

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(file);
        NodeList containerList = doc.getElementsByTagName("container");

        if (containerList.getLength() < 2) return null;

        NodeList nodeList = containerList.item(1).getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;

                if (element.hasAttribute("jcr:title") && element.getAttribute("jcr:title").equals(policyTitle)) {
                    PolicyRequest policy = new PolicyRequest();
                    policy.setName(policyTitle);

                    if (element.hasAttribute("cq:styleDefaultClasses"))
                        policy.setStyleDefaultClasses(element.getAttribute("cq:styleDefaultClasses"));
                    if (element.hasAttribute("cq:styleDefaultElement"))
                        policy.setStyleDefaultElement(element.getAttribute("cq:styleDefaultElement"));
                    if (element.hasAttribute("components"))
                        policy.setComponentPath(element.getAttribute("components"));

                    // Parse styleGroups
                    Map<String, Map<String, String>> stylesMap = new HashMap<>();
                    NodeList children = element.getChildNodes();

                    for (int j = 0; j < children.getLength(); j++) {
                        Node child = children.item(j);
                        if (child.getNodeType() == Node.ELEMENT_NODE && "cq:styleGroups".equals(child.getNodeName())) {
                            NodeList styleGroups = child.getChildNodes();
                            for (int k = 0; k < styleGroups.getLength(); k++) {
                                Node styleNode = styleGroups.item(k);
                                if (styleNode.getNodeType() == Node.ELEMENT_NODE) {
                                    Element styleEl = (Element) styleNode;
                                    String styleGroupLabel = styleEl.getAttribute("cq:styleGroupLabel");

                                    Map<String, String> styleData = stylesMap.getOrDefault(styleGroupLabel, new HashMap<>());

                                    NodeList styleTags = styleEl.getElementsByTagName("cq:styles");
                                    for (int f = 0; f < styleTags.getLength(); f++) {
                                        Node stylesNode = styleTags.item(f);
                                        if (stylesNode.getNodeType() == Node.ELEMENT_NODE) {
                                            Element stylesElement = (Element) stylesNode;
                                            NodeList itemNodes = stylesElement.getChildNodes();
                                            for (int s = 0; s < itemNodes.getLength(); s++) {
                                                Node itemNode = itemNodes.item(s);
                                                if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
                                                    Element itemElement = (Element) itemNode;
                                                    String styleLabel = itemElement.getAttribute("cq:styleLabel");
                                                    String styleClass = itemElement.getAttribute("cq:styleClasses");
                                                    styleData.put(styleLabel, styleClass);
                                                }
                                            }
                                        }
                                    }

                                    stylesMap.put(styleGroupLabel, styleData);
                                }
                            }
                        }
                    }

                    policy.setStyles(stylesMap);
                    return policy;
                }
            }
        }
        return null;
    }

}