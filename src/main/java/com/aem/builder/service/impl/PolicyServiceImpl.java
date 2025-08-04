package com.aem.builder.service.impl;

import com.aem.builder.model.policy.PolicyModel;
import com.aem.builder.model.policy.StyleGroup;
import com.aem.builder.model.policy.StyleItem;
import com.aem.builder.service.ComponentService;
import com.aem.builder.service.PolicyService;
import lombok.RequiredArgsConstructor;
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
import java.util.List;

/**
 * Service responsible for reading and writing simplified AEM component
 * policies. The implementation does not aim to cover every possible AEM
 * scenario but provides enough functionality for the UI to create and edit
 * policies within the generated project structure.
 */
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final ComponentService componentService;

    private Path projectConfBase(String project) {
        return Paths.get("generated-projects", project,
                "ui.content/src/main/content/jcr_root/conf", project);
    }

    @Override
    public List<String> getAllowedComponents(String projectName, String templateName) {
        // For now simply return all components present in the project. A more
        // elaborate implementation could inspect the template's structure to
        // determine the allowed components for the layout container.
        return componentService.fetchComponentsFromGeneratedProjects(projectName);
    }

    @Override
    public PolicyModel loadPolicy(String projectName, String templateName, String componentName) {
        Path policyFile = projectConfBase(projectName).resolve(
                Paths.get("settings/wcm/policies", componentName + ".content.xml"));
        if (!Files.exists(policyFile)) {
            return new PolicyModel();
        }
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(policyFile.toFile());
            Element root = doc.getDocumentElement();
            PolicyModel model = new PolicyModel();
            model.setTitle(root.getAttribute("jcr:title"));
            model.setDescription(root.getAttribute("jcr:description"));
            model.setDefaultCssClass(root.getAttribute("cq:styleDefaultClasses"));

            NodeList groupsNode = root.getElementsByTagName("cq:styleGroups");
            if (groupsNode.getLength() > 0) {
                Node groups = groupsNode.item(0);
                NodeList groupList = groups.getChildNodes();
                for (int i = 0; i < groupList.getLength(); i++) {
                    Node n = groupList.item(i);
                    if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element ge = (Element) n;
                    StyleGroup sg = new StyleGroup();
                    sg.setName(ge.getNodeName());
                    sg.setAllowCombination("true".equals(ge.getAttribute("cq:styleCombine")));
                    NodeList styleNodes = ge.getChildNodes();
                    for (int j = 0; j < styleNodes.getLength(); j++) {
                        Node sn = styleNodes.item(j);
                        if (sn.getNodeType() != Node.ELEMENT_NODE) continue;
                        Element se = (Element) sn;
                        StyleItem si = new StyleItem();
                        si.setName(se.getAttribute("cq:styleLabel"));
                        si.setCssClass(se.getAttribute("cq:styleClass"));
                        sg.getStyles().add(si);
                    }
                    model.getStyleGroups().add(sg);
                }
            }
            return model;
        } catch (Exception e) {
            // In case of parsing errors return an empty model.
            return new PolicyModel();
        }
    }

    @Override
    public void savePolicy(String projectName, String templateName, String componentName, PolicyModel policy) {
        try {
            Path confBase = projectConfBase(projectName);
            Path policiesDir = confBase.resolve("settings/wcm/policies");
            Files.createDirectories(policiesDir);
            Path policyFile = policiesDir.resolve(componentName + ".content.xml");

            // Build XML for policy definition
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("jcr:root");
            root.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
            root.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
            root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
            root.setAttribute("jcr:primaryType", "nt:unstructured");
            if (policy.getTitle() != null) root.setAttribute("jcr:title", policy.getTitle());
            if (policy.getDescription() != null) root.setAttribute("jcr:description", policy.getDescription());
            if (policy.getDefaultCssClass() != null) root.setAttribute("cq:styleDefaultClasses", policy.getDefaultCssClass());
            doc.appendChild(root);

            if (!policy.getStyleGroups().isEmpty()) {
                Element groups = doc.createElement("cq:styleGroups");
                groups.setAttribute("jcr:primaryType", "nt:unstructured");
                for (StyleGroup sg : policy.getStyleGroups()) {
                    Element ge = doc.createElement(sg.getName());
                    ge.setAttribute("jcr:primaryType", "nt:unstructured");
                    if (sg.isAllowCombination()) {
                        ge.setAttribute("cq:styleCombine", "true");
                    }
                    for (StyleItem si : sg.getStyles()) {
                        Element se = doc.createElement(si.getName());
                        se.setAttribute("jcr:primaryType", "nt:unstructured");
                        se.setAttribute("cq:styleLabel", si.getName());
                        se.setAttribute("cq:styleClass", si.getCssClass());
                        ge.appendChild(se);
                    }
                    groups.appendChild(ge);
                }
                root.appendChild(groups);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(policyFile.toFile()));

            // Now update template mapping file
            Path templatePoliciesFile = confBase.resolve(
                    Paths.get("settings/wcm/templates", templateName, "policies/.content.xml"));
            Files.createDirectories(templatePoliciesFile.getParent());
            Document mappingDoc;
            Element mappingRoot;
            if (Files.exists(templatePoliciesFile)) {
                mappingDoc = builder.parse(templatePoliciesFile.toFile());
                mappingRoot = mappingDoc.getDocumentElement();
            } else {
                mappingDoc = builder.newDocument();
                mappingRoot = mappingDoc.createElement("jcr:root");
                mappingRoot.setAttribute("xmlns:sling", "http://sling.apache.org/jcr/sling/1.0");
                mappingRoot.setAttribute("xmlns:cq", "http://www.day.com/jcr/cq/1.0");
                mappingRoot.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                mappingRoot.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
                mappingRoot.setAttribute("jcr:primaryType", "cq:Page");
                Element jcrContent = mappingDoc.createElement("jcr:content");
                jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
                jcrContent.setAttribute("sling:resourceType", "wcm/core/components/policies/mappings");
                Element rootMap = mappingDoc.createElement("root");
                rootMap.setAttribute("jcr:primaryType", "nt:unstructured");
                rootMap.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
                jcrContent.appendChild(rootMap);
                mappingRoot.appendChild(jcrContent);
                mappingDoc.appendChild(mappingRoot);
            }

            // Add/replace component mapping
            Element jcrContent = (Element) mappingRoot.getElementsByTagName("jcr:content").item(0);
            Element rootMap = (Element) jcrContent.getElementsByTagName("root").item(0);
            // remove existing node if present
            NodeList existing = rootMap.getElementsByTagName(componentName);
            for (int i = 0; i < existing.getLength(); i++) {
                rootMap.removeChild(existing.item(i));
            }
            Element comp = mappingDoc.createElement(componentName);
            comp.setAttribute("jcr:primaryType", "nt:unstructured");
            comp.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
            comp.setAttribute("cq:policy", projectName + "/settings/wcm/policies/" + componentName);
            rootMap.appendChild(comp);

            transformer.transform(new DOMSource(mappingDoc), new StreamResult(templatePoliciesFile.toFile()));
        } catch (Exception e) {
            throw new RuntimeException("Unable to save policy", e);
        }
    }
}
