package com.aem.builder.service.impl;

import com.aem.builder.service.TemplatePolicy;
import com.aem.builder.util.TemplateUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class TemplatePolicyImpl implements TemplatePolicy {

   @Override
    public String addPolicy(String projectname,String policyName, String componentGroups,String styleDefaultClasses,
                          String styleDefaultElement, Map<String, Map<String, String>> styles) throws Exception {


        String POLICIES_PATH =
                "generated-projects/"+projectname+"/ui.content/src/main/content/jcr_root/conf/"+projectname+"/settings/wcm/policies/.content.xml";
        File xmlFile = new File(POLICIES_PATH);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        Node containerNode = getOrCreateContainerNode(doc);

        // Create policy node
        String policyNodeName = "policy_" + System.currentTimeMillis();
        Element policy = doc.createElement(policyNodeName);
        System.out.println(policy.getAttributes()+"  is created");
        policy.setAttribute("jcr:primaryType", "nt:unstructured");
        policy.setAttribute("jcr:title", policyName);
        policy.setAttribute("sling:resourceType", "wcm/core/components/policy/policy");

        // Components as groups
        policy.setAttribute("components", componentGroups);




        policy.setAttribute("layoutDisabled", "false");

// Format ZonedDateTime in ISO-8601 without timezone ID
        String jcrDate = "{Date}" + ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));

        policy.setAttribute("jcr:lastModified", jcrDate);
        policy.setAttribute("jcr:lastModifiedBy", "admin");
        policy.setAttribute("cq:styleDefaultClasses", styleDefaultClasses);
        policy.setAttribute("cq:styleDefaultElement", styleDefaultElement);


        // jcr:content
        Element jcrContent = doc.createElement("jcr:content");
        jcrContent.setAttribute("jcr:primaryType", "nt:unstructured");
        policy.appendChild(jcrContent);

        //  Add cq:styleGroups with proper label
        if (styles != null && !styles.isEmpty()) {
            Element styleGroups = doc.createElement("cq:styleGroups");
            styleGroups.setAttribute("jcr:primaryType", "nt:unstructured");

            int groupIndex = 0;
            for (Map.Entry<String, Map<String, String>> groupEntry : styles.entrySet()) {
                String groupName = groupEntry.getKey();
                Map<String, String> styleItems = groupEntry.getValue();

                Element styleGroup = doc.createElement("item" + groupIndex++);
                styleGroup.setAttribute("jcr:primaryType", "nt:unstructured");
                styleGroup.setAttribute("cq:styleGroupLabel", groupName);

                Element cqStyles = doc.createElement("cq:styles");
                cqStyles.setAttribute("jcr:primaryType", "nt:unstructured");

                int i = 0;
                for (Map.Entry<String, String> style : styleItems.entrySet()) {
                    Element styleItem = doc.createElement("item" + i++);
                    styleItem.setAttribute("jcr:primaryType", "nt:unstructured");
                    styleItem.setAttribute("cq:styleLabel", style.getKey());
                    styleItem.setAttribute("cq:styleClasses", style.getValue());
                    styleItem.setAttribute("cq:styleElement", "div");
                    styleItem.setAttribute("cq:styleId", String.valueOf(System.currentTimeMillis() + i));
                    cqStyles.appendChild(styleItem);
                }

                styleGroup.appendChild(cqStyles);
                styleGroups.appendChild(styleGroup);
            }

            policy.appendChild(styleGroups);
        }

        containerNode.appendChild(policy);
        System.out.println("policy...."+policy.getNodeName());

        // Save XML back
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
        return policy.getNodeName();
    }

    private Node getOrCreateContainerNode(Document doc) {
        NodeList containerNodes = doc.getElementsByTagName("container");
        if (containerNodes.getLength() > 0) {
            return containerNodes.item(1);
        }
        Element container = doc.createElement("container");
        container.setAttribute("jcr:primaryType", "nt:unstructured");
        doc.getDocumentElement()
                .getElementsByTagName("components")
                .item(0)
                .appendChild(container);
        return container;
    }

    @Override
    public void assignPolicyToTemplate(String projectName, String templateName,
                                       String policyNodeName) throws Exception {
        String templatePath = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                "/settings/wcm/templates/" + templateName + "/policies/.content.xml";

        File xmlFile = new File(templatePath);
        try {
            writeFile(templatePath, TemplateUtil.policyForParticularTemplate(policyNodeName));
        }
        catch (Exception e){
            throw new IOException("something happend");
        }

    }


    void writeFile(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        System.out.println("Written file: " + path);
    }
}