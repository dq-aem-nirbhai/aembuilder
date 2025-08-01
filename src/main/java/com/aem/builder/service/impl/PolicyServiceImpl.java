package com.aem.builder.service.impl;

import com.aem.builder.model.PolicyModel;
import com.aem.builder.service.PolicyService;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class PolicyServiceImpl implements PolicyService {

    @Override
    public void createPolicy(String projectName, PolicyModel model) throws Exception {
        String base = "generated-projects/" + projectName + "/ui.content/src/main/content/jcr_root/conf/" + projectName;
        String policyId = "policy_" + System.currentTimeMillis();

        // update template mapping
        String mappingPath = base + "/settings/wcm/templates/" + model.getTemplateName() + "/policies/.content.xml";
        File mappingFile = new File(mappingPath);
        if (mappingFile.exists()) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(mappingFile);
            NodeList list = doc.getElementsByTagName("components");
            Element parent;
            if (list.getLength() > 0) {
                parent = (Element) list.item(0);
            } else {
                parent = doc.getDocumentElement();
            }
            Element elem = doc.createElement(model.getComponentName());
            elem.setAttribute("cq:policy", projectName + "/components/" + model.getComponentName() + "/" + policyId);
            elem.setAttribute("jcr:primaryType", "nt:unstructured");
            elem.setAttribute("sling:resourceType", "wcm/core/components/policies/mapping");
            parent.appendChild(elem);
            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(mappingFile));
        }

        // create policy file
        String policyDir = base + "/settings/wcm/policies/" + model.getComponentName() + "/" + policyId;
        File file = new File(policyDir, ".content.xml");
        file.getParentFile().mkdirs();
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <jcr:root xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\" xmlns:cq=\"http://www.day.com/jcr/cq/1.0\" xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"
                    jcr:primaryType=\"nt:unstructured\"
                    jcr:title=\"%s\"
                    componentGroup=\"%s\"
                    cq:styleClass=\"%s\"
                    cq:styleName=\"%s\"
                    sling:resourceType=\"%s/components/%s\"/>
                """.formatted(
                model.getPolicyName(),
                model.getGroup(),
                model.getStyleClassName(),
                model.getStyleName(),
                projectName,
                model.getComponentName());
        Files.writeString(file.toPath(), xml, StandardCharsets.UTF_8);
    }
}
