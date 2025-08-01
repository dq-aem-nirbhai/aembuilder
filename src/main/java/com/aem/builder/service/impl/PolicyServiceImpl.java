package com.aem.builder.service.impl;

import com.aem.builder.service.PolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

@Service
@Slf4j
public class PolicyServiceImpl implements PolicyService {

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public List<String> getAllowedComponents(String projectName, String templateName) {
        Map<String, String> map = getComponentPolicies(projectName, templateName);
        return new ArrayList<>(map.keySet());
    }

    @Override
    public Map<String, String> getComponentPolicies(String projectName, String templateName) {
        String path = PROJECTS_DIR + "/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/templates/" + templateName + "/policies/.content.xml";
        File file = new File(path);
        // Fallback to classpath templates if not found in generated project
        if (!file.exists()) {
            file = new File("src/main/resources/aem-templates/" + templateName + "/policies/.content.xml");
        }
        Map<String, String> result = new LinkedHashMap<>();
        if (!file.exists()) {
            log.warn("Policy file not found: {}", file.getAbsolutePath());
            return result;
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(file);
            Element root = doc.getDocumentElement();
            traverse(root, result);
        } catch (Exception e) {
            log.error("Failed to parse policy file", e);
        }
        return result;
    }

    private void traverse(Node node, Map<String, String> result) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;
            if (el.hasAttribute("sling:resourceType")) {
                String rt = el.getAttribute("sling:resourceType");
                int idx = rt.indexOf("/components/");
                if (idx != -1) {
                    String comp = rt.substring(idx + "/components/".length());
                    int slash = comp.indexOf('/');
                    if (slash != -1) comp = comp.substring(slash + 1);
                    if (el.hasAttribute("cq:policy")) {
                        result.putIfAbsent(comp, el.getAttribute("cq:policy"));
                    } else {
                        result.putIfAbsent(comp, "");
                    }
                }
            }
            NodeList list = el.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                traverse(list.item(i), result);
            }
        }
    }
}
