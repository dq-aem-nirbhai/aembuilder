package com.aem.builder.service.impl;

import com.aem.builder.model.ComponentInfo;
import com.aem.builder.model.PolicyModel;
import com.aem.builder.model.StyleGroupModel;
import com.aem.builder.model.StyleModel;
import com.aem.builder.service.PolicyService;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.springframework.util.xml.DomUtils.getChildElements;

@Slf4j
@Service
public class PolicyServiceImpl implements PolicyService {

    private static final String BASE_PATH = "generated-projects";

    /**
     * Creates a new DocumentBuilder instance.
     */
    private DocumentBuilder newBuilder() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (Exception e) {
            log.error("POLICY: Error creating DocumentBuilder", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds the configuration path for a given project.
     */
    private String buildConfPath(String project) {
        String path = BASE_PATH + "/" + project + "/ui.content/src/main/content/jcr_root/conf/" + project;
        log.info("POLICY: buildConfPath() => {}", path);
        return path;
    }

    /**
     * Gets allowed components for a template in a project.
     */
    @Override
    public List<String> getAllowedComponents(String project, String template) {
        try {
            // Step 1: Get structure container path
            String base = buildConfPath(project) + "/settings/wcm/templates/" + template;
            File structureFile = new File(base + "/structure/.content.xml");

            String containerPath = "root";
            if (structureFile.exists()) {
                Document doc = newBuilder().parse(structureFile);
                Element root = doc.getDocumentElement();
                Element content = getChild(root, "jcr:content");

                if (content != null) {
                    NodeList children = content.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        if (children.item(i) instanceof Element el) {
                            String found = findLayoutContainerPath(el, el.getTagName());
                            if (found != null) {
                                containerPath = found;
                                break;
                            }
                        }
                    }
                }
            }

            // Step 2: Get cq:policy path from template's policy mapping
            File mappingFile = new File(base + "/policies/.content.xml");
            if (!mappingFile.exists()) return List.of();

            Document mapDoc = newBuilder().parse(mappingFile);
            Element mappingRoot = getChild(mapDoc.getDocumentElement(), "jcr:content");
            if (mappingRoot == null) return List.of();

            Element current = mappingRoot;
            for (String seg : containerPath.split("/")) {
                current = getChild(current, seg);
                if (current == null) return List.of();
            }

            String policyRelPath = current.getAttribute("cq:policy");
            if (policyRelPath == null || policyRelPath.isBlank()) return List.of();

            // Step 3: Return allowed components (including resolving group entries)
            return getAllowedComponentsFromPolicy(project, policyRelPath);

        } catch (Exception e) {
            log.error("POLICY: Error getting allowed components for project: {}, template: {}", project, template, e);
            return List.of();
        }
    }

    /**
     * Gets allowed components from a policy path.
     */
    @Override
    public List<String> getAllowedComponentsFromPolicy(String project, String policyRelPath) {
        try {
            File policyFile = new File(buildConfPath(project) + "/settings/wcm/policies/.content.xml");
            if (!policyFile.exists()) return List.of();

            Document doc = newBuilder().parse(policyFile);
            Element current = doc.getDocumentElement();

            for (String seg : policyRelPath.split("/")) {
                current = getChild(current, seg);
                if (current == null) return List.of();
            }

            String raw = current.getAttribute("components");
            if (raw == null || raw.isBlank()) raw = current.getAttribute("cq:allowedComponents");
            if (raw == null || raw.isBlank()) return List.of();

            List<String> result = new ArrayList<>();
            String[] entries = raw.replace("[", "").replace("]", "").split(",");

            for (String entry : entries) {
                entry = entry.trim();
                if (entry.startsWith("group:")) {
                    String groupName = entry.substring("group:".length()).trim();
                    List<String> groupComponents = resolveComponentsByGroup(project, groupName);
                    for (String comp : groupComponents) {
                        result.add(getComponentNameOnly(comp));
                    }
                } else if (!entry.isEmpty()) {
                    result.add(getComponentNameOnly(entry));
                }
            }

            return result;

        } catch (Exception e) {
            log.error("POLICY: Error getting allowed components from policy for project: {}, path: {}", project, policyRelPath, e);
            return List.of();
        }
    }

    /**
     * Extracts the component name from a path.
     */
    @Override
    public String getComponentNameOnly(String path) {
        if (path == null || path.isBlank()) return path;
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Resolves all components belonging to a group.
     */
    @Override
    public List<String> resolveComponentsByGroup(String project, String groupName) {
        List<String> components = new ArrayList<>();
        File baseDir = new File("generated-projects/" + project + "/ui.apps/src/main/content/jcr_root/apps/" + project + "/components");

        if (!baseDir.exists()) return components;

        try {
            Files.walk(baseDir.toPath())
                    .filter(path -> path.getFileName().toString().equals(".content.xml"))
                    .forEach(path -> {
                        try {
                            Document doc = newBuilder().parse(path.toFile());
                            Element root = doc.getDocumentElement();
                            String groupAttr = root.getAttribute("componentGroup");

                            if (groupAttr != null && groupAttr.equalsIgnoreCase(groupName)) {
                                Path componentDir = path.getParent();
                                String componentName = componentDir.getFileName().toString();
                                components.add(componentName);
                            }
                        } catch (Exception ex) {
                            log.warn("POLICY: Error resolving component group '{}': {}", groupName, ex.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("POLICY: Error walking component directories for group '{}'", groupName, e);
        }

        return components;
    }

    /**
     * Recursively finds the layout container path in the template structure.
     */
    private String findLayoutContainerPath(Element element, String path) {
        String resourceType = element.getAttribute("sling:resourceType");
        boolean isContainer = resourceType != null && resourceType.contains("container");
        boolean editable = element.hasAttribute("editable") && element.getAttribute("editable").contains("true");

        if (isContainer && editable) return path;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                String found = findLayoutContainerPath(child, path + "/" + child.getTagName());
                if (found != null) return found;
            }
        }

        return isContainer ? path : null;
    }

    /**
     * Gets a direct child element by name.
     */
    private Element getChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && el.getTagName().equals(name)) {
                return el;
            }
        }
        return null;
    }

    /**
     * Checks if design dialogs exist for a list of components.
     */
    public List<ComponentInfo> checkDesignDialogs(String project, List<String> components) {
        List<ComponentInfo> result = new ArrayList<>();

        for (String component : components) {
            String path = BASE_PATH + "/" + project +
                    "/ui.apps/src/main/content/jcr_root/apps/" + project +
                    "/components/" + component + "/_cq_design_dialog/.content.xml";

            File file = new File(path);
            result.add(new ComponentInfo(component, file.exists()));
        }

        return result;
    }

    /**
     * Saves a policy for a component in a template.
     */
    @Override
    public String savePolicy(String project, String template, String componentResourceType, PolicyModel policy) {
        String policyId = policy.getId();
        if (policyId == null || policyId.isBlank()) {
            policyId = "policy_" + System.currentTimeMillis();
            policy.setId(policyId);
        }

        String base = buildConfPath(project);
        ensureFolderContent(new File(base + "/settings/wcm/policies"));
        File centralPolicyFile = new File(base + "/settings/wcm/policies/.content.xml");

        // Write the policy to the central policy file
        writePolicyFile(centralPolicyFile, componentResourceType, policy);

        // Build the correct full policy path
        // Example: chair/components/Bahubali/policy_123456789
        String componentName = componentResourceType.substring(componentResourceType.lastIndexOf("/") + 1);
        String fullPolicyPath = project + "/components/" + componentName + "/" + policyId;

        // Update template mapping
        String mappingPath = base + "/settings/wcm/templates/" + template + "/policies/.content.xml";
        updateTemplateMapping(mappingPath, fullPolicyPath);

        log.info("POLICY: Saved policy '{}' for componentResourceType '{}'", policyId, componentResourceType);
        return policyId;
    }


    /**
     * Writes or updates a policy in the central policy file.
     */
    private void writePolicyFile(File centralPolicyFile, String componentResourceType, PolicyModel policy) {
        try {
            Document doc;
            Element root;

            if (centralPolicyFile.exists()) {
                doc = newBuilder().parse(centralPolicyFile);
                root = doc.getDocumentElement();
            } else {
                doc = newBuilder().newDocument();
                root = doc.createElement("jcr:root");
                root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                root.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
                root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
                root.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
                root.setAttribute("jcr:primaryType", "cq:Page");
                doc.appendChild(root);
            }

            // Ensure policy is created under <components>/<componentResourceType>
            Element componentsEl = getOrCreateChild(doc, root, "components");
            Element componentEl = getOrCreateChild(doc, componentsEl, componentResourceType);

            // Try to find an existing policy node by ID
            Element policyEl = null;
            NodeList children = componentEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(policy.getId())) {
                    policyEl = (Element) node;
                    break;
                }
            }

            // If not found, create a new policy element
            if (policyEl == null) {
                policyEl = doc.createElement(policy.getId());
                policyEl.setAttribute("jcr:primaryType", "nt:unstructured");
                componentEl.appendChild(policyEl);
            } else {
                // If exists, remove all child nodes to clear old content
                while (policyEl.hasChildNodes()) {
                    policyEl.removeChild(policyEl.getFirstChild());
                }
            }

            // Update all common attributes
            policyEl.setAttribute("jcr:title", policy.getTitle());
            policyEl.setAttribute("jcr:description", policy.getDescription());
            policyEl.setAttribute("cq:styleDefaultClasses", policy.getDefaultCssClass());
            policyEl.setAttribute("sling:resourceType", "wcm/core/components/policy/policy");
            policyEl.setAttribute("jcr:lastModifiedBy", "admin");
            policyEl.setAttribute("jcr:lastModified", "{Date}" + getCurrentDate());

            // Add fresh <jcr:content>
            Element contentEl = doc.createElement("jcr:content");
            contentEl.setAttribute("jcr:primaryType", "nt:unstructured");
            policyEl.appendChild(contentEl);

            // Add new <cq:styleGroups>
            if (!policy.getStyleGroups().isEmpty()) {
                Element styleGroupsEl = doc.createElement("cq:styleGroups");
                styleGroupsEl.setAttribute("jcr:primaryType", "nt:unstructured");

                for (int i = 0; i < policy.getStyleGroups().size(); i++) {
                    StyleGroupModel group = policy.getStyleGroups().get(i);
                    Element groupEl = doc.createElement("item" + i);
                    groupEl.setAttribute("jcr:primaryType", "nt:unstructured");
                    groupEl.setAttribute("cq:styleGroupLabel", group.getName());
                    groupEl.setAttribute("cq:styleGroupMultiple", String.valueOf(group.isAllowCombination()));

                    Element stylesEl = doc.createElement("cq:styles");
                    stylesEl.setAttribute("jcr:primaryType", "nt:unstructured");

                    for (int j = 0; j < group.getStyles().size(); j++) {
                        StyleModel sm = group.getStyles().get(j);
                        Element styleEl = doc.createElement("item" + j);
                        styleEl.setAttribute("jcr:primaryType", "nt:unstructured");
                        styleEl.setAttribute("cq:styleLabel", sm.getName());
                        styleEl.setAttribute("cq:styleClasses", sm.getCssClass());
                        styleEl.setAttribute("cq:styleId", "style_" + System.currentTimeMillis());
                        stylesEl.appendChild(styleEl);
                    }

                    groupEl.appendChild(stylesEl);
                    styleGroupsEl.appendChild(groupEl);
                }

                policyEl.appendChild(styleGroupsEl);
            }

            writeDoc(doc, centralPolicyFile);
            log.info("POLICY: Policy file written/updated at {}", centralPolicyFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("POLICY: Failed to write/update policy in central file", e);
        }
    }

    /**
     * Gets the current date in ISO format for policy metadata.
     */
    private String getCurrentDate() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }

    /**
     * Gets all policies for a component in a project.
     */
    @Override
    public List<PolicyModel> getPolicies(String project, String componentName) {
        log.info("POLICY: Fetching policies for project: {}, component: {}", project, componentName);
        List<PolicyModel> policies = new ArrayList<>();

        try {
            File policyFile = new File(buildConfPath(project) + "/settings/wcm/policies/.content.xml");
            log.info("POLICY: Resolved policy file path: {}", policyFile.getAbsolutePath());

            if (!policyFile.exists()) {
                log.warn("POLICY: Policy file does not exist: {}", policyFile.getAbsolutePath());
                return policies;
            }

            Document doc = newBuilder().parse(policyFile);
            Element root = doc.getDocumentElement();

            Element el = getChild(root, project);
            if (el == null) return policies;

            Element componentsEl = getChild(el, "components");
            if (componentsEl == null) return policies;

            Element componentEl = getChild(componentsEl, componentName);
            if (componentEl == null) {
                return policies;
            }

            Node policyNode = componentEl.getFirstChild();
            while (policyNode != null) {
                if (policyNode instanceof Element) {
                    Element policyEl = (Element) policyNode;

                    // Only accept sling:resourceType = wcm/core/components/policy/policy
                    if (!"wcm/core/components/policy/policy".equals(policyEl.getAttribute("sling:resourceType"))) {
                        policyNode = policyNode.getNextSibling();
                        continue;
                    }

                    PolicyModel model = new PolicyModel();
                    model.setId(policyEl.getNodeName());
                    model.setTitle(policyEl.getAttribute("jcr:title"));
                    model.setDescription(policyEl.getAttribute("jcr:description"));
                    model.setDefaultCssClass(policyEl.getAttribute("cq:styleDefaultClasses"));

                    log.info("POLICY: Loaded policy: id={}, title={}", model.getId(), model.getTitle());

                    // Handle style groups if present
                    List<StyleGroupModel> styleGroups = new ArrayList<>();
                    Element styleGroupsEl = getChild(policyEl, "cq:styleGroups");
                    if (styleGroupsEl != null) {
                        Node groupNode = styleGroupsEl.getFirstChild();
                        while (groupNode != null) {
                            if (groupNode instanceof Element) {
                                Element groupEl = (Element) groupNode;
                                StyleGroupModel group = new StyleGroupModel();
                                group.setName(groupEl.getAttribute("cq:styleGroupLabel"));
                                group.setAllowCombination(Boolean.parseBoolean(groupEl.getAttribute("cq:styleGroupMultiple")));

                                log.info("POLICY:   Style group: label={}, allowCombination={}", group.getName(), group.isAllowCombination());

                                List<StyleModel> styles = new ArrayList<>();
                                Element stylesEl = getChild(groupEl, "cq:styles");
                                if (stylesEl != null) {
                                    Node styleNode = stylesEl.getFirstChild();
                                    while (styleNode != null) {
                                        if (styleNode instanceof Element) {
                                            Element styleEl = (Element) styleNode;
                                            StyleModel style = new StyleModel();
                                            style.setName(styleEl.getAttribute("cq:styleLabel"));
                                            style.setCssClass(styleEl.getAttribute("cq:styleClasses"));
                                            styles.add(style);

                                            log.info("POLICY:     Style: label={}, cssClass={}", style.getName(), style.getCssClass());
                                        }
                                        styleNode = styleNode.getNextSibling();
                                    }
                                }

                                group.setStyles(styles);
                                styleGroups.add(group);
                            }
                            groupNode = groupNode.getNextSibling();
                        }
                    }

                    model.setStyleGroups(styleGroups);
                    policies.add(model);
                }
                policyNode = policyNode.getNextSibling();
            }

            log.info("POLICY: Total policies fetched: {}", policies.size());

        } catch (Exception e) {
            log.error("POLICY: Error reading policies for project: {}, component: {}", project, componentName, e);
        }

        return policies;
    }

    /**
     * Loads a specific policy by ID for a component in a project.
     */
    @Override
    public PolicyModel loadPolicy(String project, String component, String policyId) {
        String path = buildConfPath(project) + "/settings/wcm/policies/.content.xml";
        File file = new File(path);
        log.info("POLICY: Loading policy '{}' for project: {}, component: {}", policyId, project, component);

        if (!file.exists()) {
            log.warn("POLICY: Policy file not found at: {}", file.getAbsolutePath());
            return null;
        }

        try {
            Document doc = newBuilder().parse(file);
            Element root = doc.getDocumentElement();

            Element El = getChild(root, project);
            if (El == null) return null;

            Element componentsEl = getChild(El, "components");
            if (componentsEl == null) return null;

            Element componentEl = getChild(componentsEl, component);
            if (componentEl == null) {
                return null;
            }

            Element policyEl = getChild(componentEl, policyId);
            if (policyEl == null) {
                log.warn("POLICY: Policy '{}' not found under component '{}'", policyId, component);
                return null;
            }

            // Only accept valid policy node
            if (!"wcm/core/components/policy/policy".equals(policyEl.getAttribute("sling:resourceType"))) {
                log.warn("POLICY: Invalid resourceType for policy '{}': {}", policyId, policyEl.getAttribute("sling:resourceType"));
                return null;
            }

            PolicyModel policy = new PolicyModel();
            policy.setId(policyEl.getNodeName());
            policy.setTitle(policyEl.getAttribute("jcr:title"));
            policy.setDescription(policyEl.getAttribute("jcr:description"));
            policy.setDefaultCssClass(policyEl.getAttribute("cq:styleDefaultClasses"));

            log.info("POLICY: Loaded policy: id={}, title={}", policy.getId(), policy.getTitle());

            // Handle style groups
            List<StyleGroupModel> styleGroups = new ArrayList<>();
            Element styleGroupsEl = getChild(policyEl, "cq:styleGroups");
            if (styleGroupsEl != null) {
                for (Element groupEl : getChildElements(styleGroupsEl)) {
                    StyleGroupModel group = new StyleGroupModel();
                    group.setName(groupEl.getAttribute("cq:styleGroupLabel"));
                    group.setAllowCombination(Boolean.parseBoolean(groupEl.getAttribute("cq:styleGroupMultiple")));

                    log.info("POLICY:   Style group: label={}, allowCombination={}", group.getName(), group.isAllowCombination());

                    List<StyleModel> styles = new ArrayList<>();
                    Element stylesEl = getChild(groupEl, "cq:styles");
                    if (stylesEl != null) {
                        for (Element styleEl : getChildElements(stylesEl)) {
                            StyleModel style = new StyleModel();
                            style.setName(styleEl.getAttribute("cq:styleLabel"));
                            style.setCssClass(styleEl.getAttribute("cq:styleClasses"));
                            styles.add(style);

                            log.info("POLICY:     Style: label={}, cssClass={}", style.getName(), style.getCssClass());
                        }
                    }

                    group.setStyles(styles);
                    styleGroups.add(group);
                }
            }
            policy.setStyleGroups(styleGroups);
            return policy;

        } catch (Exception e) {
            log.error("POLICY: Error loading policy '{}' for component '{}' in project '{}'", policyId, component, project, e);
            return null;
        }
    }

    /**
     * Updates the template mapping file with the new policy path.
     */
    private void updateTemplateMapping(String mappingFilePath, String policyPath) {
        try {
            File mappingFile = new File(mappingFilePath);
            Document doc;
            Element root;

            if (mappingFile.exists()) {
                doc = newBuilder().parse(mappingFile);
                root = doc.getDocumentElement();
            } else {
                doc = newBuilder().newDocument();
                root = doc.createElement("jcr:root");
                root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
                root.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
                root.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
                root.setAttribute("jcr:primaryType", "cq:Page");
                doc.appendChild(root);
            }

            // Parse policy path: e.g., chair/components/Bahubali/policy_123456789
            String[] segments = policyPath.split("/");
            if (segments.length < 4) {
                throw new IllegalArgumentException("Invalid policy path: " + policyPath);
            }

            String project = segments[0];         // e.g., chair
            String component = segments[2];       // e.g., Bahubali

            // Build jcr:content
            Element jcrContent = getOrCreateChild(doc, root, "jcr:content");
            jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
            jcrContent.setAttribute("cq:lastModified", "{Date}" + new Date().toInstant().toString());
            jcrContent.setAttribute("cq:lastModifiedBy", "admin");
            jcrContent.setAttribute("cq:policy", project + "/components/page/policy");
            jcrContent.setAttribute("sling:resourceType", "wcm/core/components/policies/mappings");

            // Build root > container
            Element rootNode = getOrCreateChild(doc, jcrContent, "root");
            rootNode.setAttribute("jcr:primaryType", "nt:unstructured");
            rootNode.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
            if (!rootNode.hasAttribute("cq:policy")) {
                rootNode.setAttribute("cq:policy", project + "/components/container/policy_" + System.currentTimeMillis());
            }

            Element container = getOrCreateChild(doc, rootNode, "container");
            container.setAttribute("jcr:primaryType", "nt:unstructured");
            container.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
            if (!container.hasAttribute("cq:policy")) {
                container.setAttribute("cq:policy", project + "/components/container/policy_" + (System.currentTimeMillis() + 1));
            }

            // Build <{project}> > <components> > <{component}>
            Element projectNode = getOrCreateChild(doc, container, project);
            projectNode.setAttribute("jcr:primaryType", "nt:unstructured");

            Element components = getOrCreateChild(doc, projectNode, "components");
            components.setAttribute("jcr:primaryType", "nt:unstructured");

            Element componentNode = getOrCreateChild(doc, components, component);
            componentNode.setAttribute("jcr:primaryType", "nt:unstructured");
            componentNode.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
            componentNode.setAttribute("cq:policy", policyPath); // 🔁 This updates if exists

            writeDoc(doc, mappingFile);
            log.info("✅ POLICY: Template mapping updated successfully for component: {}", component);

        } catch (Exception e) {
            log.error("❌ POLICY: Failed to update template mapping file: {}", mappingFilePath, e);
        }
    }

    /**
     * Ensures a folder exists and contains a .content.xml file.
     */
    private void ensureFolderContent(File folder) {
        if (!folder.exists()) folder.mkdirs();

        File contentXml = new File(folder, ".content.xml");
        if (!contentXml.exists()) {
            try {
                Document doc = newBuilder().newDocument();
                Element root = doc.createElement("jcr:root");
                root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                root.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
                root.setAttribute("jcr:primaryType", "nt:unstructured");
                doc.appendChild(root);
                writeDoc(doc, contentXml);
                log.info("POLICY: Created .content.xml at: {}", contentXml.getPath());
            } catch (Exception e) {
                log.error("POLICY: Failed to create .content.xml at: " + contentXml.getPath(), e);
            }
        }
    }

    /**
     * Gets or creates a child element by name.
     */
    private Element getOrCreateChild(Document doc, Element parent, String childName) {
        NodeList children = parent.getElementsByTagName(childName);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(childName)) {
                return (Element) child;
            }
        }
        Element newChild = doc.createElement(childName);
        newChild.setAttribute("jcr:primaryType", "nt:unstructured");
        parent.appendChild(newChild);
        return newChild;
    }

    /**
     * Writes a DOM Document to a file.
     */
    private void writeDoc(Document doc, File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
        log.info("POLICY: XML document written to {}", file.getAbsolutePath());
    }
}
