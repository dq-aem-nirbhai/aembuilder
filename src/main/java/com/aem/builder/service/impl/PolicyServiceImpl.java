package com.aem.builder.service.impl;

import com.aem.builder.model.PolicyModel;
import com.aem.builder.service.PolicyService;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyServiceImpl implements PolicyService {
    @Override
    public List<String> getAllowedComponents(String projectName, String templateName) {
        List<String> components = new ArrayList<>();
        String path = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/templates/" + templateName + "/policies/.content.xml";
        File file = new File(path);
        if (!file.exists()) {
            return components;
        }
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(file);
            NodeList roots = doc.getElementsByTagName("root");
            if (roots.getLength() > 0) {
                NodeList children = roots.item(0).getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node n = children.item(i);
                    if (n.getNodeType() == Node.ELEMENT_NODE) {
                        components.add(n.getNodeName());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return components;
    }

    @Override
    public void createPolicy(String projectName, String templateName, String componentName, PolicyModel policy) throws IOException {
        String folder = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/templates/" + templateName + "/policies";
        FileUtils.forceMkdir(new File(folder));
        File file = new File(folder, componentName + "-policy.xml");
        String xml = String.format("<policy><title>%s</title><description>%s</description></policy>",
                policy.getTitle(), policy.getDescription());
        FileUtils.writeStringToFile(file, xml, StandardCharsets.UTF_8);
    }
}
