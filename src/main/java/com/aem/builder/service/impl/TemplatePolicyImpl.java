package com.aem.builder.service.impl;

import com.aem.builder.model.PolicyRequest;
import com.aem.builder.service.TemplatePolicy;
import com.aem.builder.util.DateUtil;
import com.aem.builder.util.TemplateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.aem.builder.constants.AemProjectConstants.*;
import static com.aem.builder.constants.PolicyConstants.*;

@Service
@Slf4j
public class TemplatePolicyImpl implements TemplatePolicy {

    /**
     * Adds a new policy node to the policies XML file.
     */
    @Override
    public String addPolicy(String projectName, String policyName, String componentGroups,
                            String styleDefaultClasses, String styleDefaultElement,
                            Map<String, Map<String, Object>> styles) throws Exception {

        log.info("[addPolicy] Adding policy '{}' for project '{}'", policyName, projectName);

        String policiesPath = TemplateUtil.getPoliciesFilePath(projectName);
        File xmlFile = new File(policiesPath);

        if (!xmlFile.exists()) {
            log.error("[addPolicy] Policies file not found at path '{}'", policiesPath);
            throw new IllegalStateException("Policies file not found: " + policiesPath);
        }

        Document doc = TemplateUtil.parseXmlFile(xmlFile);
        Node containerNode = getOrCreateContainerNode(doc); // Assuming this method exists and works safely
        log.info("[addPolicy] Container node obtained");

        String policyNodeName = "policy_" + System.currentTimeMillis();
        Element policy = TemplateUtil.createElement(doc, policyNodeName, NT_UNSTRUCTURED);
        setPolicyAttributes(policy, policyName, componentGroups, styleDefaultClasses, styleDefaultElement);
        log.debug("[addPolicy] Attributes set for policy node '{}'", policyNodeName);

        Element jcrContent = TemplateUtil.createElement(doc, JCR_CONTENT_TAG, NT_UNSTRUCTURED);
        policy.appendChild(jcrContent);
        log.debug("[addPolicy] jcr:content node added for policy '{}'", policyNodeName);

        appendStyleGroups(doc, policy, styles);
        log.debug("[addPolicy] Style groups appended for policy '{}'", policyNodeName);

        containerNode.appendChild(policy);
        log.info("[addPolicy] Policy '{}' appended to container node", policyNodeName);

        saveDocument(doc, xmlFile);
        log.info("[addPolicy] Policy '{}' saved to file successfully", policyNodeName);

        return policy.getNodeName();
    }

    /**
     * Sets attributes for the policy element.
     */
    private void setPolicyAttributes(Element policy, String policyName, String componentGroups,
                                     String styleDefaultClasses, String styleDefaultElement) {

        policy.setAttribute(ATTR_STYLE_DEFAULT_CLASSES, styleDefaultClasses);
        policy.setAttribute(ATTR_STYLE_DEFAULT_ELEMENT, styleDefaultElement);
        String jcrDate = "{Date}" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        policy.setAttribute(ATTR_JCR_LAST_MODIFIED, jcrDate);
        policy.setAttribute(ATTR_JCR_LAST_MODIFIED_BY, "admin");
        policy.setAttribute(ATTR_JCR_PRIMARY_TYPE, NT_UNSTRUCTURED);
        policy.setAttribute(ATTR_JCR_TITLE, policyName);
        policy.setAttribute(ATTR_SLING_RESOURCE_TYPE, POLICY_RESOURCE_TYPE);
        policy.setAttribute(ATTR_COMPONENTS, componentGroups);
        policy.setAttribute(ATTR_LAYOUT_DISABLED, "false");
    }
    /**
     * Saves the XML document to a file with indentation for readability.
     */
    private void saveDocument(Document doc, File file) throws TransformerException, TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
        transformer.transform(new DOMSource(doc), new StreamResult(file));
    }


    /**
     * Retrieves the "container" node from the given XML document.
     * If it doesn't exist, creates a new container node under the first "components" node.
     */
    private Node getOrCreateContainerNode(Document doc) {
        log.info("[getOrCreateContainerNode] Searching for existing 'container' node...");
        NodeList containerNodes = doc.getElementsByTagName("container");
        if (containerNodes.getLength() > 0) {
            log.info("[getOrCreateContainerNode] Found existing 'container' node at index 1.");
            return containerNodes.item(1);
        }
        log.info("[getOrCreateContainerNode] No existing 'container' node found. Creating new one...");
        Element container = doc.createElement("container");
        container.setAttribute("jcr:primaryType", "nt:unstructured");
        doc.getDocumentElement()
                .getElementsByTagName("components")
                .item(0)
                .appendChild(container);
        log.info("[getOrCreateContainerNode] New 'container' node created and appended.");
        return container;
    }

    /**
     * Assigns a policy to a template by updating the template's .content.xml file.
     * Logs the action and template type, then calls TemplateUtil to update the policy reference.
     */

    @Override
    public void assignPolicyToTemplate(String projectName, String templateName,
                                       String policyNodeName) throws Exception {
        String templatePath = GENERATED_PROJECTS_PATH + projectName
                + UI_CONTENT_PATH + projectName
                + TEMPLATES_SUBPATH + templateName
                + POLICIES_SUBPATH;
        log.info("[assignPolicyToTemplate] Assigning policy '{}' to template '{}'", policyNodeName, templateName);

        String templateType = getTemplateType(projectName, templateName);
        log.info("[assignPolicyToTemplate] Template type for '{}' is '{}'", templateName, templateType);

        File xmlFile = new File(templatePath);
        try {
            TemplateUtil.updatePolicyId(templatePath, policyNodeName,projectName,templateType);
        } catch (Exception e) {
            log.error("[assignPolicyToTemplate] Failed to assign policy '{}' to template '{}'", policyNodeName, templateName, e);
            throw new IOException("Error occurred while assigning policy to template", e);
        }
    }

    /**
     * Saves a new policy or updates an existing one in the policies XML file.
     */
@Override
    public void saveOrUpdatePolicy(String projectName, String templateName, PolicyRequest request) throws Exception {
        String path = String.format(POLICIES_BASE_PATH, projectName, projectName);
        File xmlFile = new File(path);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Node containerNode = getOrCreateContainerNode(doc);
        NodeList policyNodes = containerNode.getChildNodes();
        boolean updated = false;

        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element policyEl = (Element) node;
                if (policyEl.hasAttribute(ATTR_JCR_TITLE) &&
                        policyEl.getAttribute(ATTR_JCR_TITLE).equals(request.getName())) {

                    TemplateUtil.clearChildren(policyEl);
                    TemplateUtil.setAttributes(policyEl, new String[][] {
                            {ATTR_JCR_TITLE, request.getName()},
                            {ATTR_SLING_RESOURCE_TYPE, POLICY_RESOURCE_TYPE},
                            {ATTR_COMPONENTS, request.getComponentPath()},
                            {ATTR_LAYOUT_DISABLED, "false"},
                            {ATTR_JCR_LAST_MODIFIED, DateUtil.getCurrentJcrDate()},
                            {ATTR_JCR_LAST_MODIFIED_BY, "admin"},
                            {ATTR_CQ_STYLE_DEFAULT_CLASSES, request.getStyleDefaultClasses()},
                            {ATTR_CQ_STYLE_DEFAULT_ELEMENT, request.getStyleDefaultElement()}
                    });

                    Element jcrContent = TemplateUtil.createElement(doc, JCR_CONTENT_TAG);
                    jcrContent.setAttribute(ATTR_PRIMARY_TYPE , NT_UNSTRUCTURED);
                    policyEl.appendChild(jcrContent);

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
            String policynode = addPolicy(projectName, request.getName(), request.getComponentPath(),
                    request.getStyleDefaultClasses(), request.getStyleDefaultElement(), request.getStyles());
            assignPolicyToTemplate(projectName, templateName, policynode);
        }
    }

    /**
     * Retrieves the list of existing policy names from the XML file.
     */
    @Override
    public List<String> getExistingPolicies(String projectName) {
        List<String> policies = new ArrayList<>();
        try {
            String path = TemplateUtil.getPoliciesFilePath(projectName);
            File file = new File(path);

            if (!file.exists()) {
                log.info("[getExistingPolicies] Policy file does not exist at path: {}", path);
                return policies;
            }

            Document doc = TemplateUtil.parseXmlFile(file);
            NodeList nodeList = TemplateUtil.getChildNodesOfElement(doc, "container", 1);

            if (nodeList == null) {
                log.warn("getExistingPolicies] No 'container' element found at index 1 in file: {}", path);
                return policies;
            }

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    if (element.hasAttribute(ATTR_JCR_TITLE)) {
                        String title = element.getAttribute(ATTR_JCR_TITLE);
                        policies.add(title);
                        log.debug("Found policy with title: {}", title);
                    }
                }
            }
            log.info("getExistingPolicies] Total policies found: {}", policies.size());
        } catch (Exception e) {
            log.error("getExistingPolicies] Error while fetching existing policies for project: " + projectName, e);
        }
        return policies;
    }

    /**
     * Retrieves the details of a specific policy from the XML file.
     */
    @Override
    public PolicyRequest getPolicyDetails(String projectName, String policyTitle) {
        try {
            String path = TemplateUtil.getPoliciesFilePath(projectName);
            File file = new File(path);

            if (!file.exists()) {
                log.info("[getPolicyDetails] Policy file not found at path: {}", path);
                return null;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document doc = factory.newDocumentBuilder().parse(file);

            NodeList containers = doc.getElementsByTagName("container");
            if (containers.getLength() < 2) {
                log.warn("[getPolicyDetails] No second container element found in file: {}", path);
                return null;
            }

            Node container = containers.item(1);
            NodeList policies = container.getChildNodes();

            for (int i = 0; i < policies.getLength(); i++) {
                Node node = policies.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element policyEl = (Element) node;
                    if (policyTitle.equals(policyEl.getAttribute(ATTR_JCR_TITLE))) {
                        log.info("[getPolicyDetails] Found policy with title: {}", policyTitle);
                        return parsePolicyElement(policyEl);
                    }
                }
            }

            log.info("[getPolicyDetails] Policy with title '{}' not found.", policyTitle);

        } catch (Exception e) {
            log.error("[getPolicyDetails] Error retrieving policy details for project: " + projectName, e);
        }
        return null;
    }

    /**
     * Parses a policy element from XML into a PolicyRequest object.
     */
    private PolicyRequest parsePolicyElement(Element policyEl) {
        PolicyRequest request = new PolicyRequest();
        request.setName(policyEl.getAttribute(ATTR_JCR_TITLE));
        request.setComponentPath(policyEl.getAttribute(ATTR_COMPONENTS));
        request.setStyleDefaultClasses(policyEl.getAttribute(ATTR_CQ_STYLE_DEFAULT_CLASSES));
        request.setStyleDefaultElement(policyEl.getAttribute(ATTR_CQ_STYLE_DEFAULT_ELEMENT));

        Map<String, Map<String, Object>> styleGroups = new LinkedHashMap<>();

        NodeList styleGroupsNode = policyEl.getElementsByTagName(TAG_STYLE_GROUPS);
        if (styleGroupsNode.getLength() > 0) {
            Node styleGroupNode = styleGroupsNode.item(0);
            NodeList groups = styleGroupNode.getChildNodes();
            for (int g = 0; g < groups.getLength(); g++) {
                Node groupNode = groups.item(g);
                if (groupNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element groupEl = (Element) groupNode;
                    String groupName = groupEl.getAttribute(ATTR_STYLE_GROUP_LABEL);
                    log.debug("[parsePolicyElement] Parsing style group: {}", groupName);
                    styleGroups.put(groupName, parseStyleGroup(groupEl));
                }
            }
        }
        request.setStyles(styleGroups);
        return request;
    }

    /**
     * Parses a style group element from XML into a Map structure.
     */
    private Map<String, Object> parseStyleGroup(Element groupEl) {
        Map<String, Object> groupData = new LinkedHashMap<>();
        groupData.put("multiple", "true".equals(groupEl.getAttribute(ATTR_STYLE_GROUP_MULTIPLE)));

        Map<String, Object> items = new LinkedHashMap<>();
        NodeList stylesNodes = groupEl.getElementsByTagName(TAG_CQ_STYLES);
        if (stylesNodes.getLength() > 0) {
            Node styleNode = stylesNodes.item(0);
            NodeList styleItems = styleNode.getChildNodes();
            for (int s = 0; s < styleItems.getLength(); s++) {
                Node styleItem = styleItems.item(s);
                if (styleItem.getNodeType() == Node.ELEMENT_NODE) {
                    Element styleEl = (Element) styleItem;
                    String label = styleEl.getAttribute(ATTR_STYLE_LABEL);
                    log.debug("[parseStyleGroup] Parsing style: {}", label);

                    Map<String, String> def = new HashMap<>();
                    def.put("cls", styleEl.getAttribute(ATTR_STYLE_CLASSES));
                    def.put("element", styleEl.getAttribute(ATTR_STYLE_ELEMENT));

                    items.put(label, def);
                }
            }
        }
        groupData.put("items", items);
        return groupData;
    }
    /**
     * Appends style groups and their styles to the policy XML element.
     */
    private void appendStyleGroups(Document doc, Element policyEl, Map<String, Map<String, Object>> styles) {
        if (styles == null || styles.isEmpty()) {
            log.info("[appendStyleGroups] No styles to append for policy.");
            return;
        }

        Element styleGroups = TemplateUtil.createElement(doc, TAG_STYLE_GROUPS, NT_UNSTRUCTURED);
        log.info("[appendStyleGroups] Appending style groups...");

        int groupIndex = 0;
        for (Map.Entry<String, Map<String, Object>> groupEntry : styles.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, Object> groupData = groupEntry.getValue();

            @SuppressWarnings("unchecked")
            Map<String, Object> styleItems = (Map<String, Object>) groupData.getOrDefault("items", new LinkedHashMap<>());

            boolean isMultiple = TemplateUtil.parseBoolean(groupData.get("multiple"));

            Element styleGroup = TemplateUtil.createElement(doc, TAG_STYLE_GROUP + groupIndex++, NT_UNSTRUCTURED);
            TemplateUtil.setAttribute(styleGroup,ATTR_STYLE_GROUP_LABEL, groupName);
            if (isMultiple) {
                TemplateUtil.setAttribute(styleGroup, ATTR_STYLE_GROUP_MULTIPLE, "true");
            }

            Element cqStyles = TemplateUtil.createElement(doc, TAG_CQ_STYLES, NT_UNSTRUCTURED);

            int iStyle = 0;
            for (Map.Entry<String, Object> styleEntry : styleItems.entrySet()) {
                String label = styleEntry.getKey();

                @SuppressWarnings("unchecked")
                Map<String, String> styleDef = (Map<String, String>) styleEntry.getValue();

                String cssClass = styleDef.getOrDefault("class", "");
                String element = styleDef.getOrDefault("element", "div");

                Element styleItem = TemplateUtil.createElement(doc, TAG_STYLE_ITEM + iStyle++,NT_UNSTRUCTURED);
                TemplateUtil.setAttribute(styleItem, ATTR_STYLE_LABEL, label);
                TemplateUtil.setAttribute(styleItem,ATTR_STYLE_CLASSES, cssClass);
                TemplateUtil.setAttribute(styleItem, ATTR_STYLE_ELEMENT, element);
                String styleId = String.valueOf(System.currentTimeMillis() + iStyle);
                TemplateUtil.setAttribute(styleItem, ATTR_STYLE_ID, styleId);

                log.debug("[appendStyleGroups] Appending style: {} with id {}", label, styleId);

                cqStyles.appendChild(styleItem);
            }

            styleGroup.appendChild(cqStyles);
            styleGroups.appendChild(styleGroup);
            log.info("[appendStyleGroups] Added style group '{}', multiple: {}", groupName, isMultiple);
        }

        policyEl.appendChild(styleGroups);
        log.info("[appendStyleGroups] Style groups appended successfully.");
    }

    /**
     * Retrieves the template type from the template's XML file.
     */
    public String getTemplateType(String projectName, String templateName) throws Exception {
        String templatePath = TemplateUtil.getTemplateContentFilePath(projectName, templateName);
        File xmlFile = new File(templatePath);

        if (!xmlFile.exists()) {
            log.error("[getTemplateType] Template file not found: {}", templatePath);
            throw new IOException("Template .content.xml not found: " + templatePath);
        }

        Document doc = TemplateUtil.parseXmlFile(xmlFile);
        Element root = doc.getDocumentElement();

        Element content = TemplateUtil.getFirstElementByTagName(root, JCR_CONTENT_TAG);
        if (content != null) {
            String templateType = content.getAttribute(ATTR_TEMPLATE_TYPE);
            if (templateType != null && !templateType.isEmpty()) {
                String[] parts = templateType.split("/");
                String result = parts[parts.length - 1];
                log.info("[getTemplateType] Template type found: {}", result);
                return result;
            } else {
                log.warn("[getTemplateType] cq:templateType attribute is empty in file: {}", templatePath);
            }
        } else {
            log.warn("[getTemplateType] jcr:content element not found in template file: {}", templatePath);
        }

        log.info("[getTemplateType] Returning default template type (empty string) for template: {}", templateName);
        return "";
    }
}