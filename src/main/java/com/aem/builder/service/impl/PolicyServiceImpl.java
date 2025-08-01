package com.aem.builder.service.impl;

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
    public List<StylePolicy> getPolicies(String projectName, String componentName) {
        List<StylePolicy> list = new ArrayList<>();
        File file = new File(getPolicyPath(projectName, componentName));
        if (!file.exists()) {
            return list;
        }
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(file);
            Element root = doc.getDocumentElement();
            NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) n;
                    list.add(new StylePolicy(e.getAttribute("name"), e.getAttribute("class")));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void addPolicy(String projectName, String componentName, StylePolicy policy) {
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
            Element style = doc.createElement("style");
            style.setAttribute("jcr:primaryType", "nt:unstructured");
            style.setAttribute("name", policy.getName());
            style.setAttribute("class", policy.getCssClass());
            root.appendChild(style);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
