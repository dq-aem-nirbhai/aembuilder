package com.aem.builder.service.impl;

import com.aem.builder.model.PolicyRequest;
import com.aem.builder.service.TemplatePolicy;

import org.springframework.stereotype.Service;
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
import java.io.File;

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

import java.util.concurrent.atomic.AtomicLong;

import static org.apache.jackrabbit.JcrConstants.NT_UNSTRUCTURED;

@Service
public class TemplatePolicyImpl implements TemplatePolicy {
    @Override
    public String addPolicy(String projectName, String policyName, String componentPath,
                            String styleDefaultClasses, String styleDefaultElement,
                            Map<String, Map<String, String>> styles) throws Exception {

        String cleanPath = normalizeComponentPath(componentPath);
        String policiesPath = getPoliciesPath(projectName);
        File xmlFile = new File(policiesPath);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Element componentNode = getOrCreateComponentNode(doc, cleanPath);

        // Create policy node
        String policyNodeName = "policy_" + System.currentTimeMillis();
        Element policy = doc.createElement(policyNodeName);

        populatePolicyAttributes(policy, policyName, cleanPath, styleDefaultClasses, styleDefaultElement);

        // jcr:content
        Element jcrContent = doc.createElement("jcr:content");
        jcrContent.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);
        policy.appendChild(jcrContent);

        // Add styles if present
        appendStyleGroups(doc, policy, styles, styleDefaultElement);

        componentNode.appendChild(policy);

        saveDocument(doc, xmlFile);
        return policy.getNodeName();
    }

    private void populatePolicyAttributes(Element policy, String policyName, String componentPath,
                                          String styleDefaultClasses, String styleDefaultElement) {
        policy.setAttribute("cq:styleDefaultClasses", styleDefaultClasses);
        policy.setAttribute("cq:styleDefaultElement", styleDefaultElement);
        String jcrDate = "{Date}" + ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));

        policy.setAttribute("jcr:lastModified", jcrDate);
        policy.setAttribute("jcr:lastModifiedBy", "admin");
        policy.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);
        policy.setAttribute("jcr:title", policyName);
        policy.setAttribute("sling:resourceType", "wcm/core/components/policy/policy");
        policy.setAttribute("components", componentPath);
        policy.setAttribute("layoutDisabled", "false");
    }
    // put this at class level (outside methods)
    private static final AtomicLong STYLE_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

    private void appendStyleGroups(Document doc, Element policy,
                                   Map<String, Map<String, String>> styles, String styleDefaultElement) {
        if (styles == null || styles.isEmpty()) return;

        Element styleGroups = doc.createElement("cq:styleGroups");
        styleGroups.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);

        int groupIndex = 0;
        for (Map.Entry<String, Map<String, String>> groupEntry : styles.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, String> styleItems = groupEntry.getValue();

            Element styleGroup = doc.createElement("item" + groupIndex++);
            styleGroup.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);
            styleGroup.setAttribute("cq:styleGroupLabel", groupName);

            Element cqStyles = doc.createElement("cq:styles");
            cqStyles.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);

            int i = 0;
            for (Map.Entry<String, String> style : styleItems.entrySet()) {
                Element styleItem = doc.createElement("item" + i++);
                styleItem.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);
                styleItem.setAttribute("cq:styleClasses", style.getValue());

                // ✅ now globally unique across all groups and policies
                String uniqueId = String.valueOf(STYLE_ID_GENERATOR.getAndIncrement());
                styleItem.setAttribute("cq:styleId", uniqueId);

                styleItem.setAttribute("cq:styleElement", "div");
                styleItem.setAttribute("cq:styleLabel", style.getKey());
                cqStyles.appendChild(styleItem);
            }

            styleGroup.appendChild(cqStyles);
            styleGroups.appendChild(styleGroup);
        }

        policy.appendChild(styleGroups);
    }

    private String normalizeComponentPath(String componentPath) {
        if (componentPath == null) return "";
        String clean = componentPath.trim();
        if (clean.startsWith("[")) clean = clean.substring(1);
        if (clean.endsWith("]")) clean = clean.substring(0, clean.length() - 1);
        int comma = clean.indexOf(',');
        if (comma >= 0) clean = clean.substring(0, comma);
        return clean.trim();
    }

    private Element getOrCreateComponentNode(Document doc, String componentPath) {
        // componentPath expected like /apps/project/components/comp
        String rel = componentPath;
        if (rel.startsWith("/apps/")) {
            rel = rel.substring("/apps/".length());
        }
        String[] segments = rel.split("/");
        Element current = doc.getDocumentElement();
        for (String seg : segments) {
            if (seg.isBlank()) continue;
            current = getOrCreateChild(doc, current, seg);
        }
        return current;
    }

    private Element getOrCreateChild(Document doc, Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        Element child = doc.createElement(name);
        child.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);
        parent.appendChild(child);
        return child;
    }

    private Element findPolicyByTitle(Document doc, String title) {
        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) n;
                if (title.equals(el.getAttribute("jcr:title"))) {
                    return el;
                }
            }
        }
        return null;
    }

    private void saveDocument(Document doc, File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), new StreamResult(file));
    }

    private String getPoliciesPath(String projectName) {
        return "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/policies/.content.xml";
    }

    @Override
    public void assignPolicyToTemplate(String projectName, String templateName,
                                       String componentPath, String policyNodeName) throws Exception {
        String templatePath = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/templates/" + templateName + "/policies/.content.xml";

        File xmlFile = new File(templatePath);
        if (!xmlFile.exists()) {
            // Create minimal mapping structure if missing
            Files.createDirectories(xmlFile.getParentFile().toPath());
            String skeleton = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0" jcr:primaryType="cq:Page">
                        <jcr:content jcr:primaryType="nt:unstructured" sling:resourceType="wcm/core/components/policies/mappings">
                            <root jcr:primaryType="nt:unstructured" sling:resourceType="wcm/core/components/policies/mapping"/>
                        </jcr:content>
                    </jcr:root>
                    """;
            Files.writeString(xmlFile.toPath(), skeleton, StandardCharsets.UTF_8);
        }

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Element root = (Element) doc.getElementsByTagName("root").item(0);
        String cleanPath = normalizeComponentPath(componentPath);
        String rel = cleanPath;
        int idx = rel.indexOf("/components/");
        if (idx >= 0) {
            rel = rel.substring(idx + "/components/".length());
        }
        String[] segments = rel.split("/");
        Element current = root;
        for (String seg : segments) {
            if (seg.isBlank()) continue;
            current = getOrCreateChild(doc, current, seg);
            current.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
        }

        current.setAttribute("cq:policy", projectName + "/components/" + rel + "/" + policyNodeName);

        saveDocument(doc, xmlFile);
    }

    @Override
    public String addPolicyToTemplate(String projectName, String templateName,
                                      String policyName, String componentPath,
                                      String styleDefaultClasses, String styleDefaultElement,
                                      Map<String, Map<String, String>> styles) throws Exception {
        String nodeName = addPolicy(projectName, policyName, componentPath,
                styleDefaultClasses, styleDefaultElement, styles);
        assignPolicyToTemplate(projectName, templateName, componentPath, nodeName);
        return nodeName;
    }

    void writeFile(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        System.out.println("Written file: " + path);
    }

    @Override
    public void saveOrUpdatePolicy(String projectName, String templateName, PolicyRequest request) throws Exception {
        String policiesPath = getPoliciesPath(projectName);
        File xmlFile = new File(policiesPath);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Element policyEl = findPolicyByTitle(doc, request.getName());
        if (policyEl != null) {
            while (policyEl.hasChildNodes()) {
                policyEl.removeChild(policyEl.getFirstChild());
            }
            populatePolicyAttributes(policyEl, request.getName(),
                    normalizeComponentPath(request.getComponentPath()),
                    request.getStyleDefaultClasses(),
                    request.getStyleDefaultElement());

            Element jcrContent = doc.createElement("jcr:content");
            jcrContent.setAttribute("jcr:primaryType", NT_UNSTRUCTURED);
            policyEl.appendChild(jcrContent);

            appendStyleGroups(doc, policyEl, request.getStyles(), request.getStyleDefaultElement());
            saveDocument(doc, xmlFile);
            String policynode = policyEl.getNodeName();
            assignPolicyToTemplate(projectName, templateName, request.getComponentPath(), policynode);
            System.out.println("✅ Existing policy updated: " + request.getName());
        } else {
            String policynode = addPolicy(projectName, request.getName(),
                    request.getComponentPath(),
                    request.getStyleDefaultClasses(),
                    request.getStyleDefaultElement(),
                    request.getStyles());
            assignPolicyToTemplate(projectName, templateName, request.getComponentPath(), policynode);
            System.out.println("➕ New policy created: " + request.getName());
        }
    }

    @Override
    public List<String> getExistingPolicies(String projectName) throws Exception {
        List<String> policies = new ArrayList<>();
        File file = new File(getPoliciesPath(projectName));
        if (!file.exists()) return policies;

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(file);
        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
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
        File file = new File(getPoliciesPath(projectName));
        if (!file.exists()) return null;

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(file);
        Element element = findPolicyByTitle(doc, policyTitle);
        if (element == null) return null;

        PolicyRequest policy = new PolicyRequest();
        policy.setName(policyTitle);
        policy.setStyleDefaultClasses(element.getAttribute("cq:styleDefaultClasses"));
        policy.setStyleDefaultElement(element.getAttribute("cq:styleDefaultElement"));
        policy.setComponentPath(element.getAttribute("components"));

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
                        Map<String, String> styleData = new HashMap<>();

                        NodeList cqStyles = styleEl.getElementsByTagName("cq:styles");
                        for (int f = 0; f < cqStyles.getLength(); f++) {
                            Node stylesNode = cqStyles.item(f);
                            if (stylesNode.getNodeType() == Node.ELEMENT_NODE) {
                                NodeList itemNodes = stylesNode.getChildNodes();
                                for (int s = 0; s < itemNodes.getLength(); s++) {
                                    Node itemNode = itemNodes.item(s);
                                    if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
                                        Element itemElement = (Element) itemNode;
                                        styleData.put(
                                                itemElement.getAttribute("cq:styleLabel"),
                                                itemElement.getAttribute("cq:styleClasses")
                                        );
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