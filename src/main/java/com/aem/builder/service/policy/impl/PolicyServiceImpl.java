package com.aem.builder.service.policy.impl;

import com.aem.builder.model.policy.PolicyModel;
import com.aem.builder.model.policy.Style;
import com.aem.builder.model.policy.StyleGroup;
import com.aem.builder.service.policy.PolicyService;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class PolicyServiceImpl implements PolicyService {
    private static final String BASE = "generated-projects";

    private Path getTemplateStructurePath(String project, String template) {
        return Paths.get(BASE, project,
                "ui.content/src/main/content/jcr_root/conf/" + project +
                        "/settings/wcm/templates/" + template + "/structure/.content.xml");
    }

    private Path getTemplatePoliciesPath(String project, String template) {
        return Paths.get(BASE, project,
                "ui.content/src/main/content/jcr_root/conf/" + project +
                        "/settings/wcm/templates/" + template + "/policies/.content.xml");
    }

    private Path getComponentPolicyFolder(String project, String component) {
        return Paths.get(BASE, project,
                "ui.content/src/main/content/jcr_root/conf/" + project +
                        "/settings/wcm/policies/" + component);
    }

    @Override
    public List<String> getAllowedComponents(String projectName, String templateName) {
        List<String> components = new ArrayList<>();
        Path p = getTemplateStructurePath(projectName, templateName);
        if (!Files.exists(p)) return components;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(p.toFile());
            NodeList nodes = doc.getElementsByTagName("*");
            Set<String> set = new HashSet<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) n;
                    String rt = e.getAttribute("sling:resourceType");
                    String prefix = projectName + "/components/";
                    if (rt != null && rt.startsWith(prefix)) {
                        String comp = rt.substring(prefix.length());
                        set.add(comp);
                    }
                }
            }
            components.addAll(set);
        } catch (Exception e) {
            log.error("Error reading allowed components", e);
        }
        return components;
    }

    @Override
    public List<String> getPoliciesForComponent(String projectName, String componentName) {
        List<String> policies = new ArrayList<>();
        Path folder = getComponentPolicyFolder(projectName, componentName);
        if (!Files.isDirectory(folder)) return policies;
        File[] files = folder.toFile().listFiles((dir, name) -> name.endsWith(".content.xml"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName().replace(".content.xml", "");
                policies.add(name);
            }
        }
        return policies;
    }

    @Override
    public PolicyModel readPolicy(String projectName, String componentName, String policyName) {
        Path file = getComponentPolicyFolder(projectName, componentName)
                .resolve(policyName + ".content.xml");
        if (!Files.exists(file)) return null;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(file.toFile());
            Element root = doc.getDocumentElement();
            PolicyModel model = new PolicyModel();
            model.setPolicyName(policyName);
            model.setStyleDefaultClasses(root.getAttribute("cq:styleDefaultClasses"));
            NodeList groups = root.getElementsByTagName("cq:styleGroups");
            if (groups.getLength() > 0) {
                Element groupsEl = (Element) groups.item(0);
                NodeList groupNodes = groupsEl.getChildNodes();
                for (int i = 0; i < groupNodes.getLength(); i++) {
                    Node g = groupNodes.item(i);
                    if (g.getNodeType() == Node.ELEMENT_NODE) {
                        Element ge = (Element) g;
                        StyleGroup sg = new StyleGroup();
                        sg.setName(ge.getNodeName());
                        NodeList styleNodes = ge.getElementsByTagName("styles");
                        if (styleNodes.getLength() > 0) {
                            Element stylesEl = (Element) styleNodes.item(0);
                            NodeList sNodes = stylesEl.getChildNodes();
                            for (int j = 0; j < sNodes.getLength(); j++) {
                                Node sn = sNodes.item(j);
                                if (sn.getNodeType() == Node.ELEMENT_NODE) {
                                    Element se = (Element) sn;
                                    Style st = new Style();
                                    st.setName(se.getNodeName());
                                    st.setClassName(se.getAttribute("cq:styleClasses"));
                                    st.setTitle(se.getAttribute("jcr:title"));
                                    st.setDefaultStyle("true".equals(se.getAttribute("cq:default")));
                                    sg.getStyles().add(st);
                                }
                            }
                        }
                        model.getStyleGroups().add(sg);
                    }
                }
            }
            return model;
        } catch (Exception e) {
            log.error("Error reading policy", e);
            return null;
        }
    }

    @Override
    public void savePolicy(String projectName, String templateName, String componentName, PolicyModel policy) {
        try {
            Path folder = getComponentPolicyFolder(projectName, componentName);
            Files.createDirectories(folder);
            Path file = folder.resolve(policy.getPolicyName() + ".content.xml");

            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.newDocument();
            Element root = doc.createElement("jcr:root");
            root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
            root.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
            root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
            root.setAttribute("jcr:primaryType", "nt:unstructured");
            if (policy.getStyleDefaultClasses() != null) {
                root.setAttribute("cq:styleDefaultClasses", policy.getStyleDefaultClasses());
            }
            if (!policy.getStyleGroups().isEmpty()) {
                Element groupsEl = doc.createElement("cq:styleGroups");
                groupsEl.setAttribute("jcr:primaryType", "nt:unstructured");
                for (StyleGroup sg : policy.getStyleGroups()) {
                    Element gEl = doc.createElement(sg.getName());
                    gEl.setAttribute("jcr:primaryType", "nt:unstructured");
                    if (!sg.getStyles().isEmpty()) {
                        Element stylesEl = doc.createElement("styles");
                        stylesEl.setAttribute("jcr:primaryType", "nt:unstructured");
                        for (Style st : sg.getStyles()) {
                            Element stEl = doc.createElement(st.getName());
                            stEl.setAttribute("jcr:primaryType", "nt:unstructured");
                            if (st.getClassName() != null) stEl.setAttribute("cq:styleClasses", st.getClassName());
                            if (st.getTitle() != null) stEl.setAttribute("jcr:title", st.getTitle());
                            if (st.isDefaultStyle()) stEl.setAttribute("cq:default", "true");
                            stylesEl.appendChild(stEl);
                        }
                        gEl.appendChild(stylesEl);
                    }
                    groupsEl.appendChild(gEl);
                }
                root.appendChild(groupsEl);
            }
            doc.appendChild(root);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer tr = tf.newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.transform(new DOMSource(doc), new StreamResult(file.toFile()));

            // update mapping file
            Path mapping = getTemplatePoliciesPath(projectName, templateName);
            Files.createDirectories(mapping.getParent());
            Document mappingDoc;
            if (Files.exists(mapping)) {
                mappingDoc = b.parse(mapping.toFile());
            } else {
                mappingDoc = b.newDocument();
                Element rootMapping = mappingDoc.createElement("jcr:root");
                rootMapping.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
                rootMapping.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
                rootMapping.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                rootMapping.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
                rootMapping.setAttribute("jcr:primaryType", "cq:Page");
                Element jcrContent = mappingDoc.createElement("jcr:content");
                jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
                jcrContent.setAttribute("sling:resourceType", "wcm/core/components/policies/mappings");
                rootMapping.appendChild(jcrContent);
                mappingDoc.appendChild(rootMapping);
            }
            Element rootEl = mappingDoc.getDocumentElement();
            Element jcrContent = (Element) rootEl.getElementsByTagName("jcr:content").item(0);
            Element componentEl = mappingDoc.createElement(componentName);
            componentEl.setAttribute("cq:policy", projectName + "/components/" + componentName + "/" + policy.getPolicyName());
            componentEl.setAttribute("jcr:primaryType", "nt:unstructured");
            componentEl.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
            jcrContent.appendChild(componentEl);
            Transformer tr2 = TransformerFactory.newInstance().newTransformer();
            tr2.setOutputProperty(OutputKeys.INDENT, "yes");
            tr2.transform(new DOMSource(mappingDoc), new StreamResult(mapping.toFile()));
        } catch (Exception e) {
            log.error("Error saving policy", e);
        }
    }

    @Override
    public void deletePolicy(String projectName, String templateName, String componentName, String policyName) {
        try {
            Path file = getComponentPolicyFolder(projectName, componentName)
                    .resolve(policyName + ".content.xml");
            Files.deleteIfExists(file);
            Path mapping = getTemplatePoliciesPath(projectName, templateName);
            if (Files.exists(mapping)) {
                DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document doc = b.parse(mapping.toFile());
                Element root = doc.getDocumentElement();
                Element jcrContent = (Element) root.getElementsByTagName("jcr:content").item(0);
                NodeList children = jcrContent.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node n = children.item(i);
                    if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(componentName)) {
                        Element e = (Element) n;
                        String pol = e.getAttribute("cq:policy");
                        if (pol != null && pol.endsWith("/" + policyName)) {
                            jcrContent.removeChild(e);
                            break;
                        }
                    }
                }
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.transform(new DOMSource(doc), new StreamResult(mapping.toFile()));
            }
        } catch (Exception e) {
            log.error("Error deleting policy", e);
        }
    }
}
