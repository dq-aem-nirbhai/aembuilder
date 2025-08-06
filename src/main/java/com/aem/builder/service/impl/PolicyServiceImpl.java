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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Back end service that handles AEM style component policies. The implementation
 * reads and writes the same .content.xml structure that AEM uses so the
 * generated project can be imported into an actual AEM instance.
 */
@Slf4j
@Service
public class PolicyServiceImpl implements PolicyService {

    private static final String PROJECTS_DIR = "generated-projects";

    private DocumentBuilder newBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String confRoot(String project) {
        return PROJECTS_DIR + "/" + project + "/ui.content/src/main/content/jcr_root/conf/" + project;
    }

    // ---------------------------------------------------------------------
    // Read operations
    // ---------------------------------------------------------------------

    @Override
    public List<String> getAllowedComponents(String project, String template) {
        File mappingFile = new File(confRoot(project)
                + "/settings/wcm/templates/" + template + "/policies/.content.xml");
        if (!mappingFile.exists()) {
            return List.of();
        }
        try {
            Document doc = newBuilder().parse(mappingFile);
            Element policyEl = getPolicyElement(doc);
            if (policyEl == null) return List.of();
            String rel = policyEl.getAttribute("cq:policy");
            if (rel == null || rel.isBlank()) return List.of();
            File policyFile = new File(confRoot(project)
                    + "/settings/wcm/policies/" + rel + "/.content.xml");
            if (!policyFile.exists()) return List.of();
            Document polDoc = newBuilder().parse(policyFile);
            String allowed = polDoc.getDocumentElement().getAttribute("cq:allowedComponents");
            if (allowed == null || allowed.isBlank()) return List.of();
            return Arrays.stream(allowed.replaceAll("[\\[\\]]", "").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Unable to read allowed components", e);
            return List.of();
        }
    }

    private Element getPolicyElement(Document mappingDoc) {
        Element root = mappingDoc.getDocumentElement();
        Element content = getChild(root, "jcr:content");
        return getChild(content, "root");
    }

    @Override
    public Map<String, PolicyModel> getPolicies(String project, String componentResourceType) {
        Map<String, PolicyModel> result = new LinkedHashMap<>();
        File dir = new File(confRoot(project) + "/settings/wcm/policies/" + componentResourceType);
        if (!dir.exists()) return result;
        File[] folders = dir.listFiles(File::isDirectory);
        if (folders == null) return result;
        for (File folder : folders) {
            File content = new File(folder, ".content.xml");
            if (!content.exists()) continue;
            PolicyModel model = readPolicy(content);
            if (model != null) {
                model.setId(folder.getName());
                result.put(folder.getName(), model);
            }
        }
        return result;
    }

    @Override
    public PolicyModel loadPolicy(String project, String componentResourceType, String policyId) {
        File file = new File(confRoot(project) + "/settings/wcm/policies/" + componentResourceType
                + "/" + policyId + "/.content.xml");
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
                List<String> groupNames = Arrays.stream(groupsAttr.replaceAll("[\\[\\]]", "").split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                Element groupsNode = getChild(root, "cq:styleGroups");
                for (String name : groupNames) {
                    Element gEl = getChild(groupsNode, name);
                    if (gEl == null) continue;
                    StyleGroupModel group = new StyleGroupModel();
                    group.setName(name);
                    group.setAllowCombination(Boolean.parseBoolean(gEl.getAttribute("cq:styleAllowedCombination")));
                    String[] names = gEl.getAttribute("cq:styleNames").replaceAll("[\\[\\]]", "").split(",");
                    String[] classes = gEl.getAttribute("cq:styleClasses").replaceAll("[\\[\\]]", "").split(",");
                    List<StyleModel> styles = new ArrayList<>();
                    for (int i = 0; i < names.length; i++) {
                        String n = names[i].trim();
                        if (n.isEmpty()) continue;
                        String c = i < classes.length ? classes[i].trim() : "";
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
            log.error("Failed to parse policy file {}", file, e);
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Write operations
    // ---------------------------------------------------------------------

    @Override
    public String savePolicy(String project, String template, String componentResourceType, PolicyModel policy) {
        String id = (policy.getId() != null && !policy.getId().isBlank())
                ? policy.getId()
                : "policy_" + System.currentTimeMillis();

        String base = confRoot(project);

        ensureFolderContent(new File(base + "/settings/wcm/policies"));

        File policyDir = new File(base + "/settings/wcm/policies/" + componentResourceType + "/" + id);
        policyDir.mkdirs();
        writePolicyFile(new File(policyDir, ".content.xml"), policy);

        File mapping = new File(base + "/settings/wcm/templates/" + template + "/policies/.content.xml");
        updateTemplateMapping(mapping, componentResourceType + "/" + id);

        ensureFolderContent(new File(base + "/settings/wcm/templates/" + template));

        return id;
    }

    private void ensureFolderContent(File folder) {
        folder.mkdirs();
        File content = new File(folder, ".content.xml");
        if (content.exists()) return;
        try {
            Document doc = newBuilder().newDocument();
            Element root = doc.createElement("jcr:root");
            root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
            root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
            root.setAttribute("jcr:primaryType", "sling:Folder");
            doc.appendChild(root);
            writeDoc(doc, content);
        } catch (Exception e) {
            log.error("Unable to create folder .content.xml {}", content, e);
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
                String groups = policy.getStyleGroups().stream()
                        .map(StyleGroupModel::getName)
                        .collect(Collectors.joining(","));
                root.setAttribute("cq:styleGroups", "[" + groups + "]");
                Element groupsNode = doc.createElement("cq:styleGroups");
                for (StyleGroupModel g : policy.getStyleGroups()) {
                    Element gEl = doc.createElement(g.getName());
                    gEl.setAttribute("jcr:primaryType", "nt:unstructured");
                    gEl.setAttribute("cq:styleAllowedCombination", "{Boolean}" + g.isAllowCombination());
                    String names = g.getStyles().stream()
                            .map(StyleModel::getName)
                            .collect(Collectors.joining(","));
                    String classes = g.getStyles().stream()
                            .map(StyleModel::getCssClass)
                            .collect(Collectors.joining(","));
                    gEl.setAttribute("cq:styleNames", "[" + names + "]");
                    gEl.setAttribute("cq:styleClasses", "[" + classes + "]");
                    groupsNode.appendChild(gEl);
                }
                root.appendChild(groupsNode);
            }
            doc.appendChild(root);
            writeDoc(doc, file);
        } catch (Exception e) {
            log.error("Unable to write policy file {}", file, e);
        }
    }

    private void updateTemplateMapping(File mappingFile, String relPolicyPath) {
        try {
            if (!mappingFile.exists()) {
                mappingFile.getParentFile().mkdirs();
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
                rootNode.setAttribute("cq:policy", relPolicyPath);
                content.appendChild(rootNode);
                root.appendChild(content);
                doc.appendChild(root);
                writeDoc(doc, mappingFile);
                return;
            }
            Document doc = newBuilder().parse(mappingFile);
            Element root = doc.getDocumentElement();
            Element content = getChild(root, "jcr:content");
            if (content == null) {
                content = doc.createElement("jcr:content");
                content.setAttribute("jcr:primaryType", "nt:unstructured");
                content.setAttribute("sling:resourceType", "wcm/core/components/policies/mappings");
                root.appendChild(content);
            }
            Element rootNode = getChild(content, "root");
            if (rootNode == null) {
                rootNode = doc.createElement("root");
                rootNode.setAttribute("jcr:primaryType", "nt:unstructured");
                rootNode.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
                content.appendChild(rootNode);
            }
            rootNode.setAttribute("cq:policy", relPolicyPath);
            writeDoc(doc, mappingFile);
        } catch (Exception e) {
            log.error("Unable to update template policy mapping {}", mappingFile, e);
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
