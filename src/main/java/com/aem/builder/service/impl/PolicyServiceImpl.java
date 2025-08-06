package com.aem.builder.service.impl;

import com.aem.builder.model.policy.PolicyModel;
import com.aem.builder.model.policy.StyleGroupModel;
import com.aem.builder.model.policy.StyleModel;
import com.aem.builder.service.PolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PolicyServiceImpl implements PolicyService {

    private static final String BASE_PATH = "generated-projects";

    private DocumentBuilder newBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildConfPath(String project) {
        return BASE_PATH + "/" + project + "/ui.content/src/main/content/jcr_root/conf/" + project;
    }

    @Override
    public List<String> getAllowedComponents(String project, String template) {
        try {
            String mappingPath = buildConfPath(project) + "/settings/wcm/templates/" + template + "/policies/.content.xml";
            File mappingFile = new File(mappingPath);
            if (!mappingFile.exists()) {
                return List.of();
            }
            Document doc = newBuilder().parse(mappingFile);
            Element root = doc.getDocumentElement();
            String policyPath = null;
            NodeList all = root.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Element el = (Element) all.item(i);
                if (el.hasAttribute("cq:policy")) {
                    policyPath = el.getAttribute("cq:policy");
                    break;
                }
            }
            if (policyPath == null) {
                return List.of();
            }
            String policyFilePath = buildConfPath(project) + "/settings/wcm/policies/" + policyPath + "/.content.xml";
            File policyFile = new File(policyFilePath);
            if (!policyFile.exists()) {
                return List.of();
            }
            Document policyDoc = newBuilder().parse(policyFile);
            Element polRoot = policyDoc.getDocumentElement();

            // First try attribute-based format: cq:allowedComponents="[a,b]"
            String allowed = polRoot.getAttribute("cq:allowedComponents");
            if (allowed != null && !allowed.isBlank()) {
                allowed = allowed.replace("[", "").replace("]", "");
                return Arrays.stream(allowed.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            // Fallback: check child node representation
            Element allowedEl = getChild(polRoot, "cq:allowedComponents");
            if (allowedEl == null) {
                allowedEl = getChild(polRoot, "allowedComponents");
            }
            if (allowedEl == null) {
                return List.of();
            }
            List<String> components = new ArrayList<>();
            NodeList comps = allowedEl.getElementsByTagName("*");
            for (int i = 0; i < comps.getLength(); i++) {
                Element comp = (Element) comps.item(i);
                if (comp.hasAttribute("sling:resourceType")) {
                    components.add(comp.getAttribute("sling:resourceType"));
                } else if (comp.hasAttribute("component")) {
                    components.add(comp.getAttribute("component"));
                }
            }
            return components;
        } catch (Exception e) {
            log.error("Failed to read allowed components", e);
            return List.of();
        }
    }

    @Override
    public Map<String, PolicyModel> getPolicies(String project, String componentResourceType) {
        Map<String, PolicyModel> result = new LinkedHashMap<>();
        String policyDirPath = buildConfPath(project) + "/settings/wcm/policies/" + componentResourceType;
        File dir = new File(policyDirPath);
        if (!dir.exists()) {
            return result;
        }
        File[] policies = dir.listFiles(File::isDirectory);
        if (policies == null) return result;
        for (File p : policies) {
            File content = new File(p, ".content.xml");
            if (content.exists()) {
                PolicyModel model = readPolicy(content);
                if (model != null) {
                    model.setId(p.getName());
                    result.put(p.getName(), model);
                }
            }
        }
        return result;
    }

    @Override
    public PolicyModel loadPolicy(String project, String componentResourceType, String policyId) {
        String path = buildConfPath(project) + "/settings/wcm/policies/" + componentResourceType + "/" + policyId + "/.content.xml";
        File file = new File(path);
        if (!file.exists()) return null;
        return readPolicy(file);
    }

    private PolicyModel readPolicy(File file) {
        try {
            Document doc = newBuilder().parse(file);
            Element root = doc.getDocumentElement();
            PolicyModel model = new PolicyModel();
            model.setTitle(root.getAttribute("jcr:title"));
            model.setDescription(root.getAttribute("jcr:description"));
            model.setDefaultCssClass(root.getAttribute("cq:styleDefaultClasses"));
            String groupsAttr = root.getAttribute("cq:styleGroups");
            if (groupsAttr != null && !groupsAttr.isBlank()) {
                groupsAttr = groupsAttr.replace("[", "").replace("]", "");
                List<String> groupNames = Arrays.stream(groupsAttr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                Element groupsElement = getChild(root, "cq:styleGroups");
                for (String gName : groupNames) {
                    Element gEl = getChild(groupsElement, gName);
                    if (gEl == null) continue;
                    StyleGroupModel group = new StyleGroupModel();
                    group.setName(gName);
                    group.setAllowCombination(Boolean.parseBoolean(gEl.getAttribute("cq:styleAllowedCombination")));
                    String names = gEl.getAttribute("cq:styleNames").replace("[", "").replace("]", "");
                    String classes = gEl.getAttribute("cq:styleClasses").replace("[", "").replace("]", "");
                    String[] nArr = names.split(",");
                    String[] cArr = classes.split(",");
                    List<StyleModel> styles = new ArrayList<>();
                    for (int i = 0; i < nArr.length; i++) {
                        String n = nArr[i].trim();
                        if (n.isEmpty()) continue;
                        String c = i < cArr.length ? cArr[i].trim() : "";
                        StyleModel sm = new StyleModel();
                        sm.setName(n);
                        sm.setCssClass(c);
                        styles.add(sm);
                    }
                    group.setStyles(styles);
                    model.getStyleGroups().add(group);
                }
            }
            return model;
        } catch (Exception e) {
            log.error("Error reading policy", e);
            return null;
        }
    }

    @Override
    public String savePolicy(String project, String template, String componentResourceType, PolicyModel policy) {
        String policyId = policy.getId();
        if (policyId == null || policyId.isBlank()) {
            policyId = "policy_" + System.currentTimeMillis();
        }
        String base = buildConfPath(project);
        File policyDir = new File(base + "/settings/wcm/policies/" + componentResourceType + "/" + policyId);
        policyDir.mkdirs();
        // ensure root policies .content.xml
        ensureFolderContent(new File(base + "/settings/wcm/policies"));
        // write policy file
        File policyFile = new File(policyDir, ".content.xml");
        writePolicyFile(policyFile, policy);
        // update template policy mapping
        String mappingPath = base + "/settings/wcm/templates/" + template + "/policies/.content.xml";
        updateTemplateMapping(mappingPath, componentResourceType + "/" + policyId);
        // touch template root .content.xml (ensure exists)
        ensureFolderContent(new File(base + "/settings/wcm/templates/" + template));
        return policyId;
    }

    private void ensureFolderContent(File folder) {
        folder.mkdirs();
        File content = new File(folder, ".content.xml");
        if (!content.exists()) {
            try {
                Document doc = newBuilder().newDocument();
                Element root = doc.createElement("jcr:root");
                root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
                root.setAttribute("jcr:primaryType", "sling:Folder");
                doc.appendChild(root);
                writeDoc(doc, content);
            } catch (Exception e) {
                log.error("Failed to write folder .content.xml", e);
            }
        }
    }

    private void writePolicyFile(File file, PolicyModel policy) {
        try {
            Document doc = newBuilder().newDocument();
            Element root = doc.createElement("jcr:root");
            root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
            root.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
            root.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
            root.setAttribute("jcr:primaryType", "cq:Policy");
            if (policy.getTitle() != null) root.setAttribute("jcr:title", policy.getTitle());
            if (policy.getDescription() != null) root.setAttribute("jcr:description", policy.getDescription());
            if (policy.getDefaultCssClass() != null && !policy.getDefaultCssClass().isBlank()) {
                root.setAttribute("cq:styleDefaultClasses", policy.getDefaultCssClass());
            }
            if (!policy.getStyleGroups().isEmpty()) {
                String groupNames = policy.getStyleGroups().stream()
                        .map(StyleGroupModel::getName)
                        .collect(Collectors.joining(","));
                root.setAttribute("cq:styleGroups", "[" + groupNames + "]");
                Element groupsEl = doc.createElement("cq:styleGroups");
                for (StyleGroupModel sg : policy.getStyleGroups()) {
                    Element gEl = doc.createElement(sg.getName());
                    gEl.setAttribute("jcr:primaryType", "nt:unstructured");
                    gEl.setAttribute("cq:styleAllowedCombination", "{Boolean}" + sg.isAllowCombination());
                    String names = sg.getStyles().stream().map(StyleModel::getName).collect(Collectors.joining(","));
                    String classes = sg.getStyles().stream().map(StyleModel::getCssClass).collect(Collectors.joining(","));
                    gEl.setAttribute("cq:styleNames", "[" + names + "]");
                    gEl.setAttribute("cq:styleClasses", "[" + classes + "]");
                    groupsEl.appendChild(gEl);
                }
                root.appendChild(groupsEl);
            }
            doc.appendChild(root);
            writeDoc(doc, file);
        } catch (Exception e) {
            log.error("Failed to write policy file", e);
        }
    }

    private void updateTemplateMapping(String mappingPath, String policyRelPath) {
        try {
            File file = new File(mappingPath);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                Document doc = newBuilder().newDocument();
                Element root = doc.createElement("jcr:root");
                root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
                root.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
                root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                root.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
                root.setAttribute("jcr:primaryType", "cq:Page");
                Element content = doc.createElement("jcr:content");
                content.setAttribute("jcr:primaryType", "nt:unstructured");
                content.setAttribute("sling:resourceType", "wcm/core/components/policies/mappings");
                Element rootNode = doc.createElement("root");
                rootNode.setAttribute("jcr:primaryType", "nt:unstructured");
                rootNode.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
                rootNode.setAttribute("cq:policy", policyRelPath);
                content.appendChild(rootNode);
                root.appendChild(content);
                doc.appendChild(root);
                writeDoc(doc, file);
                return;
            }
            Document doc = newBuilder().parse(file);
            Element root = doc.getDocumentElement();
            Element content = getChild(root, "jcr:content");
            Element rootNode = getChild(content, "root");
            if (rootNode == null) {
                rootNode = doc.createElement("root");
                rootNode.setAttribute("jcr:primaryType", "nt:unstructured");
                rootNode.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
                content.appendChild(rootNode);
            }
            rootNode.setAttribute("cq:policy", policyRelPath);
            writeDoc(doc, file);
        } catch (Exception e) {
            log.error("Failed to update template policy mapping", e);
        }
    }

    private Element getChild(Element parent, String name) {
        if (parent == null) return null;
        NodeList nodes = parent.getElementsByTagName(name);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getParentNode() == parent) {
                return (Element) nodes.item(i);
            }
        }
        return null;
    }

    private void writeDoc(Document doc, File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            transformer.transform(new DOMSource(doc), new StreamResult(fos));
        }
    }
}
