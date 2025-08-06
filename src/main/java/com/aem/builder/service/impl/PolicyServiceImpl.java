package com.aem.builder.service.impl;

import com.aem.builder.model.policy.PolicyModel;
import com.aem.builder.model.policy.StyleGroupModel;
import com.aem.builder.model.policy.StyleModel;
import com.aem.builder.service.PolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PolicyServiceImpl implements PolicyService {

    private static final String BASE_PATH = "generated-projects";

    private DocumentBuilder newBuilder() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildConfPath(String project) {
        String path = BASE_PATH + "/" + project + "/ui.content/src/main/content/jcr_root/conf/" + project;
        log.info("🔧 buildConfPath() => {}", path);
        return path;
    }




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
            return List.of();
        }
    }



    private List<String> getAllowedComponentsFromPolicy(String project, String policyRelPath) {
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
                    // Extract only component names from group resolution
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
            return List.of();
        }
    }

    private String getComponentNameOnly(String path) {
        if (path == null || path.isBlank()) return path;
        String[] parts = path.split("/");
        return parts[parts.length - 1];  // Last segment is component name
    }


    private List<String> resolveComponentsByGroup(String project, String groupName) {
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
                                Path componentDir = path.getParent(); // directory of component
                                String componentName = componentDir.getFileName().toString(); // just the name
                                components.add(componentName);
                            }
                        } catch (Exception ignore) {}
                    });
        } catch (IOException e) {
            // Optionally log the error
        }

        return components;
    }




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

    private Element getChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && el.getTagName().equals(name)) {
                return el;
            }
        }
        return null;
    }



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
        writePolicyFile(centralPolicyFile, componentResourceType, policy);

        String mappingPath = base + "/settings/wcm/templates/" + template + "/policies/.content.xml";
        updateTemplateMapping(mappingPath, componentResourceType + "/" + policyId);

        return policyId;
    }


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

            Element componentEl = getOrCreateChild(doc, root, componentResourceType);

            // Dynamically generate policy node name
            String policyNodeName = "policy_" + System.currentTimeMillis();

            Element policyEl = doc.createElement(policyNodeName);
            policyEl.setAttribute("jcr:primaryType", "nt:unstructured");
            policyEl.setAttribute("jcr:title", policy.getTitle());
            policyEl.setAttribute("jcr:description", policy.getDescription());
            policyEl.setAttribute("cq:styleDefaultClasses", policy.getDefaultCssClass());
            policyEl.setAttribute("sling:resourceType", "wcm/core/components/policy/policy");
            policyEl.setAttribute("jcr:lastModifiedBy", "admin");
            policyEl.setAttribute("jcr:lastModified", "{Date}" + getCurrentDate());

            // Add <jcr:content>
            Element contentEl = doc.createElement("jcr:content");
            contentEl.setAttribute("jcr:primaryType", "nt:unstructured");
            policyEl.appendChild(contentEl);

            // Add <cq:styleGroups> if any style groups exist
            if (!policy.getStyleGroups().isEmpty()) {
                Element styleGroupsEl = doc.createElement("cq:styleGroups");
                styleGroupsEl.setAttribute("jcr:primaryType", "nt:unstructured");

                for (int i = 0; i < policy.getStyleGroups().size(); i++) {
                    StyleGroupModel group = policy.getStyleGroups().get(i);
                    Element groupEl = doc.createElement("item" + i);
                    groupEl.setAttribute("jcr:primaryType", "nt:unstructured");
                    groupEl.setAttribute("cq:styleGroupLabel", group.getName());
                    groupEl.setAttribute("cq:styleGroupMultiple", String.valueOf(group.isAllowCombination()));

                    // <cq:styles>
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

            componentEl.appendChild(policyEl);
            writeDoc(doc, centralPolicyFile);
        } catch (Exception e) {
            log.error("❌ Failed to merge policy into central file", e);
        }
    }

    private String getCurrentDate() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }
    @Override
    public Map<String, PolicyModel> getPolicies(String project, String componentName) {
        Map<String, PolicyModel> policies = new LinkedHashMap<>();

        try {
            // Resolve resourceType using component's content.xml
            String componentPath = "generated-projects/" + project + "/ui.apps/src/main/content/jcr_root/apps/" + project + "/components/" + componentName + "/.content.xml";
            File componentFile = new File(componentPath);
            if (!componentFile.exists()) {
                log.warn("⚠️ Component not found: {}", componentPath);
                return policies;
            }

            Document componentDoc = newBuilder().parse(componentFile);
            Element componentRoot = componentDoc.getDocumentElement();
            String resourceType = componentRoot.getAttribute("sling:resourceType");

            if (resourceType == null || resourceType.isEmpty()) {
                // Fallback: assume standard resourceType
                resourceType = "apps/" + project + "/components/" + componentName;
            }

            // Now read the policies file
            File policyFile = new File(buildConfPath(project) + "/settings/wcm/policies/.content.xml");
            if (!policyFile.exists()) return policies;

            Document policyDoc = newBuilder().parse(policyFile);
            Element root = policyDoc.getDocumentElement();

            // Traverse through resourceType segments (e.g., apps → project → components → component)
            String[] segments = resourceType.split("/");
            Element current = root;

            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                current = getChild(current, segment);
                if (current == null) {
                    log.warn("⚠️ Segment '{}' not found in path: {}", segment, resourceType);
                    return policies;
                }
            }

            // Extract all policy nodes under this component resourceType
            NodeList children = current.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) child;
                    String name = el.getNodeName();

                    if (name.startsWith("policy_")) {
                        PolicyModel policy = new PolicyModel();
                        policy.setId(name);
                        policy.(el.getAttribute("jcr:title"));
                        policy.setDescription(el.getAttribute("jcr:description"));
                        policy.setAttributes(getAttributes(el));
                        policies.put(name, policy);
                    }
                }
            }

        } catch (Exception e) {
            log.error("❌ Error while loading policies for component '{}': {}", componentName, e.getMessage());
        }

        return policies;
    }

    @Override
    public PolicyModel loadPolicy(String project, String componentResourceType, String policyId) {
        try {
            File policyFile = new File(buildConfPath(project) + "/settings/wcm/policies/.content.xml");
            if (!policyFile.exists()) return null;

            Document doc = newBuilder().parse(policyFile);
            Element root = doc.getDocumentElement();
            Element componentEl = getChild(root, componentResourceType);
            if (componentEl == null) return null;

            Element policyEl = getChild(componentEl, policyId);
            if (policyEl == null) return null;

            PolicyModel model = new PolicyModel();
            model.setId(policyId);
            model.setTitle(policyEl.getAttribute("jcr:title"));
            model.setDescription(policyEl.getAttribute("jcr:description"));
            model.setDefaultCssClass(policyEl.getAttribute("cq:styleDefaultClasses"));

            Element styleGroupsEl = getChild(policyEl, "cq:styleGroups");
            if (styleGroupsEl != null) {
                List<StyleGroupModel> groups = new ArrayList<>();
                NodeList groupNodes = styleGroupsEl.getChildNodes();
                for (int i = 0; i < groupNodes.getLength(); i++) {
                    Node groupNode = groupNodes.item(i);
                    if (groupNode.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element groupEl = (Element) groupNode;

                    StyleGroupModel group = new StyleGroupModel();
                    group.setName(groupEl.getAttribute("cq:styleGroupLabel"));

                    Element stylesEl = getChild(groupEl, "cq:styles");
                    if (stylesEl != null) {
                        List<StyleModel> styles = new ArrayList<>();
                        NodeList styleNodes = stylesEl.getChildNodes();
                        for (int j = 0; j < styleNodes.getLength(); j++) {
                            Node styleNode = styleNodes.item(j);
                            if (styleNode.getNodeType() != Node.ELEMENT_NODE) continue;
                            Element styleEl = (Element) styleNode;

                            StyleModel sm = new StyleModel();
                            sm.setName(styleEl.getAttribute("cq:styleLabel"));
                            sm.setCssClass(styleEl.getAttribute("cq:styleClasses"));
                            styles.add(sm);
                        }
                        group.setStyles(styles);
                    }

                    groups.add(group);
                }

                model.setStyleGroups(groups);
            }

            return model;
        } catch (Exception e) {
            log.error("❌ Failed to load policy", e);
            return null;
        }
    }






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
                root.setAttribute("jcr:primaryType", "nt:unstructured");
                doc.appendChild(root);
            }

            String[] segments = policyPath.split("/");
            String compType = segments[0];
            String policyId = segments[1];

            Element componentEl = getOrCreateChild(doc, root, compType);
            componentEl.setAttribute("cq:policy", policyPath);

            writeDoc(doc, mappingFile);
        } catch (Exception e) {
            log.error("❌ Failed to update template mapping file: " + mappingFilePath, e);
        }
    }

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
            } catch (Exception e) {
                log.error("⚠️ Failed to create .content.xml at: " + contentXml.getPath(), e);
            }
        }
    }

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



    private void removeIfExists(Element parent, String nodeName) {
        NodeList children = parent.getElementsByTagName(nodeName);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(nodeName)) {
                parent.removeChild(child);
                return;
            }
        }
    }

    private void writeDoc(Document doc, File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
    }








}
