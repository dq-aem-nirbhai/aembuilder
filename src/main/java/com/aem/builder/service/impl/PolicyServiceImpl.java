package com.aem.builder.service.impl;

import com.aem.builder.model.ComponentPolicy;
import com.aem.builder.model.StyleGroup;
import com.aem.builder.model.StylePolicy;
import com.aem.builder.service.PolicyService;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyServiceImpl implements PolicyService {

    private String getPolicyPath(String project, String component) {
        return "generated-projects/" + project +
                "/ui.content/src/main/content/jcr_root/conf/" + project +
                "/settings/wcm/policies/" + component + "/styles/.content.xml";
    }

    @Override
    public List<ComponentPolicy> getPolicies(String projectName, String componentName) {
        List<ComponentPolicy> list = new ArrayList<>();
        File file = new File(getPolicyPath(projectName, componentName));
        if (!file.exists()) {
            return list;
        }
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(file);
            Element root = doc.getDocumentElement();
            NodeList nodes = root.getElementsByTagName("policy");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element pEl = (Element) nodes.item(i);
                ComponentPolicy cp = new ComponentPolicy();
                cp.setTitle(pEl.getAttribute("title"));
                cp.setDescription(pEl.getAttribute("description"));
                cp.setDefaultClass(pEl.getAttribute("defaultClass"));
                List<StyleGroup> groups = new ArrayList<>();
                NodeList groupNodes = pEl.getElementsByTagName("group");
                for (int j = 0; j < groupNodes.getLength(); j++) {
                    Element gEl = (Element) groupNodes.item(j);
                    StyleGroup sg = new StyleGroup();
                    sg.setName(gEl.getAttribute("name"));
                    sg.setCombine(Boolean.parseBoolean(gEl.getAttribute("combine")));
                    List<StylePolicy> styles = new ArrayList<>();
                    NodeList styleNodes = gEl.getElementsByTagName("style");
                    for (int k = 0; k < styleNodes.getLength(); k++) {
                        Element sEl = (Element) styleNodes.item(k);
                        styles.add(new StylePolicy(sEl.getAttribute("name"), sEl.getAttribute("class")));
                    }
                    sg.setStyles(styles);
                    groups.add(sg);
                }
                cp.setGroups(groups);
                list.add(cp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void addPolicy(String projectName, String componentName, ComponentPolicy policy) {
        try {
            File file = new File(getPolicyPath(projectName, componentName));
            Document doc;
            Element root;
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            if (file.exists()) {
                doc = builder.parse(file);
                root = doc.getDocumentElement();
            } else {
                file.getParentFile().mkdirs();
                doc = builder.newDocument();
                root = doc.createElement("jcr:root");
                root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                root.setAttribute("jcr:primaryType", "nt:unstructured");
                doc.appendChild(root);
            }
            Element pEl = doc.createElement("policy");
            pEl.setAttribute("jcr:primaryType", "nt:unstructured");
            pEl.setAttribute("title", policy.getTitle());
            if (policy.getDescription() != null) {
                pEl.setAttribute("description", policy.getDescription());
            }
            if (policy.getDefaultClass() != null) {
                pEl.setAttribute("defaultClass", policy.getDefaultClass());
            }
            for (StyleGroup group : policy.getGroups()) {
                Element gEl = doc.createElement("group");
                gEl.setAttribute("jcr:primaryType", "nt:unstructured");
                gEl.setAttribute("name", group.getName());
                gEl.setAttribute("combine", String.valueOf(group.isCombine()));
                for (StylePolicy sp : group.getStyles()) {
                    Element sEl = doc.createElement("style");
                    sEl.setAttribute("jcr:primaryType", "nt:unstructured");
                    sEl.setAttribute("name", sp.getName());
                    sEl.setAttribute("class", sp.getCssClass());
                    gEl.appendChild(sEl);
                }
                pEl.appendChild(gEl);
            }
            root.appendChild(pEl);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
