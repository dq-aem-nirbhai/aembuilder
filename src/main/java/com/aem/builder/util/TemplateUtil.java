package com.aem.builder.util;

import com.aem.builder.constants.AemProjectConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import lombok.extern.slf4j.Slf4j;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.aem.builder.constants.AemProjectConstants.*;
import static com.aem.builder.constants.PolicyConstants.*;
import static com.aem.builder.util.AemUtil.getAppId;
@Slf4j
public class TemplateUtil {

    /**
     * Returns the path to the templates directory for the given project.
     */
    public static String getTemplatePath(String projectName) {
        String appId=getAppId(PROJECTS_DIR,projectName);
        return PROJECTS_DIR + "/" + projectName + UI_CONTENT_PATH + appId + WCM_TEMPLATES_PATH;
    }

    /**
     * Returns the full path to the .content.xml file of the templates directory.
     */
     public static String getPathOfTemplateContent(String projectName){
        return getTemplatePath(projectName)+CONTENT_FILE;
     }

    /**
     * Returns the path where template types are stored for the given project.
     */
    public static String getTemplateTypesPath(String projectName) {
        String appId=getAppId(PROJECTS_DIR,projectName);
        return PROJECTS_DIR + "/" + projectName + UI_CONTENT_PATH + appId + WCM_TEMPLATE_TYPES_PATH;
    }

    /**
     * Returns the path to the specific template folder.
     */
    public static String getTemplateParent(String projectName, String templateName){
        return getTemplatePath(projectName)+"/"+templateName;
    }

    /**
     * Returns the path to the initial files folder for the given template.
     */
    public static String getIntialFilePath(String projectName,String templateName){
        return getTemplateParent(projectName,templateName)+INITIAL;
    }

    /**
     * Returns the path to the structure files folder for the given template.
     */
    public static String getStructureFilePath(String projectName,String templateName){
        return getTemplateParent(projectName,templateName)+STRUCTURE;
    }

    /**
     * Returns the path to the policies folder for the given template.
     */
    public static String getPoliciesFilePath(String projectName,String templateName){
        return getTemplateParent(projectName,templateName)+POLICIES;
    }

    /**
     * Returns the path to the template's root content file (.content.xml).
     */
    public static String getRootContentFilePath(String projectName, String templateName){
        return getTemplateParent(projectName,templateName)+CONTENT_FILE;
    }

    /**
     * Returns the path to the initial content file for the given template.
     */
    public static String getIntialContentFile(String projectName,String templateName){
        return getIntialFilePath( projectName, templateName)+CONTENT_FILE;
    }

    /**
     * Returns the path to the structure content file for the given template.
     */
    public static String getStructureContentFile(String projectName,String templateName){
        return getStructureFilePath( projectName, templateName)+CONTENT_FILE;
    }

    /**
     * Returns the path to the policies content file for the given template.
     */
    public static String getPoliciesContentFile(String projectName,String templateName){
        return getPoliciesFilePath(projectName,templateName)+CONTENT_FILE;
    }

    /**
     * Returns the path to the policies file for the given project (used by multiple templates).
     */

    public static String getPoliciesFilePath(String projectName) {
       String appId= AemUtil.getAppId(PROJECTS_DIR,projectName);
        return String.format(POLICIES_BASE_PATH, projectName, appId);
    }

    /**
     * Returns the path to the template's content file (.content.xml) for the given template.
     */
    public static String getTemplateContentFilePath(String projectName, String templateName) {
        String appId=getAppId(PROJECTS_DIR,projectName);
        return AemProjectConstants.PROJECTS_DIR + "/" + projectName +
                UI_CONTENT_PATH  + appId +
                WCM_TEMPLATES_PATH+ "/" + templateName +
                CONTENT_FILE;
    }

    /**
     * Writes the provided content to a file at the specified path.
     * It creates any missing directories in the file path.
     */
 public static   void writeFile(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a new XML element with the given name.
     */
    public static Element createElement(Document doc, String name) {
        return doc.createElement(name);
    }

    /**
     * Removes all child nodes from the given element.
     */
    public static void clearChildren(Element element) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }
    }

    /**
     * Sets multiple attributes on an element from a 2D array of key-value pairs.
     */
    public static void setAttributes(Element element, String[][] attributes) {
        for (String[] attr : attributes) {
            element.setAttribute(attr[0], attr[1]);
        }
    }

    /**
     * Parses the given XML file into a DOM Document.
     */
    public static Document parseXmlFile(File file) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(file);
    }

    /**
     * Returns the child nodes of a specific element (by tag name and index) from a document.
     */
    public static NodeList getChildNodesOfElement(Document doc, String tagName, int index) {
        NodeList list = doc.getElementsByTagName(tagName);
        if (list.getLength() > index) {
            Node node = list.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return node.getChildNodes();
            }
        }
        return null;
    }

    /**
     * Creates a new XML element with a given name and sets its jcr:primaryType.
     */
    public static Element createElement(Document doc, String name, String primaryType) {
        Element el = doc.createElement(name);
        el.setAttribute(ATTR_JCR_PRIMARY_TYPE, primaryType);
        return el;
    }

    /**
     * Sets a single attribute on the given element.
     */
    public static void setAttribute(Element el, String attrName, String value) {
        el.setAttribute(attrName, value);
    }

    /**
     * Safely parses a boolean value from either Boolean or String type input.
     */
    public static boolean parseBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    /**
     * Returns the first child element of a parent matching the given tag name.
     */
    public static Element getFirstElementByTagName(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0 && nodes.item(0).getNodeType() == Node.ELEMENT_NODE) {
            return (Element) nodes.item(0);
        }
        return null;
    }

    /**
     * Generates the XML content for the policies file (policies.xml) used in an XF page template.
     *
     * The XML sets up policy mappings for the container component, linking it to a default policy node.
     * This helps define how components within the template should behave according to the policy configurations.
     */
    public static String generatePoliciesXmlXf(String projectname){
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                jcr:primaryType="cq:Page">
                <jcr:content
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="wcm/core/components/policies/mappings">
                    <root
                        cq:policy="%s/components/container/policy_1575040440977"
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="wcm/core/components/policies/mapping"/>
                </jcr:content>
            </jcr:root>
            
            """.formatted(projectname);
    }

    /**
     * Generates the XML content for a template's structure file (structure.xml)
     * tailored for an XF page in the AEM project.
     *
     * The XML defines the page structure, including device groups, template reference,
     * resource types, and responsive grid layout settings.
     */
    public static String generateStructureContentXmlXf(String projectname,String templatename){
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                jcr:primaryType="cq:Page">
                <jcr:content
                    cq:deviceGroups="[mobile/groups/responsive]"
                    cq:template="/conf/%s/settings/wcm/templates/%s"
                    jcr:primaryType="cq:PageContent"
                    sling:resourceType="%s/components/xfpage">
                    <root
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="%s/components/container"
                        editable="{Boolean}true"
                        layout="responsiveGrid"/>
                    <cq:responsive jcr:primaryType="nt:unstructured">
                        <breakpoints jcr:primaryType="nt:unstructured">
                            <phone
                                jcr:primaryType="nt:unstructured"
                                title="Smaller Screen"
                                width="{Long}768"/>
                            <tablet
                                jcr:primaryType="nt:unstructured"
                                title="Tablet"
                                width="{Long}1200"/>
                        </breakpoints>
                    </cq:responsive>
                </jcr:content>
            </jcr:root>
            """.formatted(projectname,templatename,projectname,projectname);
    }

    /**
     * Generates the XML content for the root node of a page template definition.
     *
     * This XML represents the template structure, including its title, type, description,
     * and status. It defines both the template node and its associated jcr:content node,
     * which stores metadata and configuration settings required by AEM.
     */

    public static String getTemplateRootXmlPage(String templateName, String projectName, String templateType,
                                                String status, String description) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root
            xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
            xmlns:jcr="http://www.jcp.org/jcr/1.0"
            xmlns:cq="http://www.day.com/jcr/cq/1.0"
            jcr:primaryType="cq:Template"
            jcr:title="%s">
            <jcr:content
                cq:templateType="/conf/%s/settings/wcm/template-types/%s"
                jcr:description="%s"
                jcr:primaryType="cq:PageContent"
                jcr:title="%s"
                status="%s"/>
        </jcr:root>
        """.formatted(templateName, projectName, templateType, description, templateName, status);
    }
    /**
     * Generates the initial .content.xml structure for a newly created AEM page template.
     *
     * This XML defines the root page node, its jcr:content, and a basic container structure.
     * It sets up the template reference, resource types, and default responsive grid layout.
     */
    public static String getInitialXmlPage(String projectname,String templatename) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
                <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                    jcr:primaryType="cq:Page">
                    <jcr:content
                        cq:template="/conf/%s/settings/wcm/templates/%s"
                        jcr:primaryType="cq:PageContent"
                        sling:resourceType="%s/components/page">
                        <root
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="%s/components/container"
                            layout="responsiveGrid"/>
                           \s
                    </jcr:content>
                </jcr:root>
               \s""".formatted(projectname,templatename,projectname,projectname,projectname);
    }

    /**
     * Generates the structure .content.xml for a page template in AEM.
     *
     * This XML defines the page node, its jcr:content, responsive breakpoints,
     * a root container, and an experience fragment header. It sets up template references
     * and resource types for the project.
     */
    public static String getStructureXmlPage(String templatename,String projectnane) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root
            xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
            xmlns:cq="http://www.day.com/jcr/cq/1.0"
            xmlns:jcr="http://www.jcp.org/jcr/1.0"
            xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
            jcr:primaryType="cq:Page">
            <jcr:content
                cq:deviceGroups="[mobile/groups/responsive]"
                cq:lastModified="{Date}2025-07-21T10:21:18.990+05:30"
                cq:lastModifiedBy="admin"
                cq:template="/conf/%s/settings/wcm/templates/%s"
                jcr:primaryType="cq:PageContent"
                sling:resourceType="%s/components/page">
                <root
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="%s/components/container"
                    layout="responsiveGrid">
                    <experiencefragment-header
                                            jcr:primaryType="nt:unstructured"
                                            sling:resourceType="%s/components/experiencefragment"
                                            fragmentVariationPath="/content/experience-fragments/%s/language-masters/en/site/header/master"/>
                                     \s
                    <container
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="%s/components/container"
                        editable="{Boolean}true"
                        layout="responsiveGrid"/>
                </root>
                <cq:responsive jcr:primaryType="nt:unstructured">
                    <breakpoints jcr:primaryType="nt:unstructured">
                        <phone
                            jcr:primaryType="nt:unstructured"
                            title="Smaller Screen"
                            width="{Long}768"/>
                        <tablet
                            jcr:primaryType="nt:unstructured"
                            title="Tablet"
                            width="{Long}1200"/>
                    </breakpoints>
                </cq:responsive>
            </jcr:content>
        </jcr:root>
        """.formatted(projectnane,templatename,projectnane,projectnane,projectnane,projectnane,projectnane); // Use projectName for resource types
    }

    /**
     * Generates the policies .content.xml for a page template in AEM.
     *
     * This XML defines the page node with jcr:content for policy mappings, including
     * root and container nodes, each referencing default component policies for the project.
     * The XML structure allows the template to inherit and apply predefined policies.
     */
    public static String getPoliciesPage(String projectname) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
        jcr:primaryType="cq:Page">
                <jcr:content
        cq:policy="%s/components/page/policy"
        jcr:primaryType="nt:unstructured"
        sling:resourceType="wcm/core/components/policies/mappings">
                <root
        cq:policy="%s/components/container/policy_1574694950110"
        jcr:primaryType="nt:unstructured"
        sling:resourceType="wcm/core/components/policies/mapping">
                <container
        cq:policy="%s/components/container/policy_1574695586800"
        jcr:primaryType="nt:unstructured"
        sling:resourceType="wcm/core/components/policies/mapping"/>
                </root>
                </jcr:content>
                </jcr:root>
               \s""".formatted(projectname,projectname,projectname);
    }

    /**
     * Generates the initial .content.xml for an Experience Fragment (XF) page in AEM.
     *
     * This XML defines the root page and jcr:content nodes for the XF template,
     * including tags, template reference, variant type, and the root container.
     * It ensures the experience fragment is correctly initialized with a responsive grid layout.
     */
    public static String getIntialContentXf(String projectname,String templatename){
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                    jcr:primaryType="cq:Page">
                    <jcr:content
                        cq:tags="[experience-fragments:variation/web]"
                        cq:template="/conf/%s/settings/wcm/templates/%s"
                        cq:xfVariantType="web"
                        jcr:primaryType="cq:PageContent"
                        sling:resourceType="%s/components/xfpage">
                        <root
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="%s/components/container"
                            layout="responsiveGrid"/>
                    </jcr:content>
                </jcr:root>
                
                """.formatted(projectname,templatename,projectname,projectname);
    }

    /**
     * Updates the cq:policy attribute in a template's XML file for a specific project and template type.
     *
     * Depending on the template type ("page" or "xf"), this method locates the relevant node:
     * - For "page" templates, it updates the first <container> node.
     * - For "xf" (experience fragment) templates, it updates the first <root> node.
     *
     * The new policy path is constructed using the project name and provided policy ID.
     * After updating the XML node, the changes are written back to the same file with indentation.
     */
    public static void updatePolicyId(String filePath, String newPolicyId, String projectName, String templateType) {
        try {
            // Load XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(filePath));
            doc.getDocumentElement().normalize();

            String newPolicyPath = projectName + CONTAINER_PATH+"/" + newPolicyId;

            if ("page".equalsIgnoreCase(templateType)) {
                // Find <container> node
                NodeList containerNodes = doc.getElementsByTagName(CONTAINER_NODE);
                if (containerNodes.getLength() > 0) {
                    Element containerElement = (Element) containerNodes.item(0);
                    containerElement.setAttribute(ATTR_POLICY, newPolicyPath);
                }
            } else if ("xf".equalsIgnoreCase(templateType)) {
                // Find <root> node
                NodeList rootNodes = doc.getElementsByTagName(ROOT_NODE);
                if (rootNodes.getLength() > 0) {
                    Element rootElement = (Element) rootNodes.item(0);
                    rootElement.setAttribute(ATTR_POLICY, newPolicyPath);
                }
            }

            // Write changes back to file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);

            log.info("Policy updated successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the .content.xml file when the template type changes.
     * Handles both transitions:
     *   1. page → xf
     *   2. xf → page
     */
    public static void updateStructureXml(File structureFile, String oldType, String newType, String projectName,String oldTemplateName,String newTemplateName) throws Exception {
        log.info("old template name -> {}, new template name -> {}",oldTemplateName,newTemplateName);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(structureFile);
        doc.getDocumentElement().normalize();

        Element contentEl = (Element) doc.getElementsByTagName(JCR_CONTENT_TAG).item(0);
        // Handle transitions
        if (!oldTemplateName.equalsIgnoreCase(newTemplateName)) {
            log.info("Template name changed → {}", newTemplateName);
            setcqTempatepath(contentEl, projectName, newTemplateName);
        }
        if (!oldType.equals(newType)) {

            if (contentEl == null) {
                log.warn("⚠️ No <jcr:content> found in XML.");
                return;
            }
            Element rootEl = (Element) contentEl.getElementsByTagName(ROOT_NODE).item(0);
            if (rootEl == null) {
                log.warn("⚠️ No <root> found in XML.");
                return;
            }

// ✅ Update cq:template when name changes
            if (!oldTemplateName.equalsIgnoreCase(newTemplateName)) {
                log.info("Template name changed → {}", newTemplateName);
                setcqTempatepath(contentEl, projectName, newTemplateName);
            }

// ✅ Handle type conversion if needed
            if (!oldType.equals(newType)) {
                if ("page".equals(oldType) && "xf".equals(newType)) {
                    convertPageToXf(doc, contentEl, rootEl, projectName);
                } else if ("xf".equals(oldType) && "page".equals(newType)) {
                    convertXfToPage(doc, contentEl, rootEl, projectName);
                }
            }


        }
        saveXml(doc, structureFile);
    }

    /**
     * Converts structure from Page → XF type.
     * Removes experiencefragment-header and inner container nodes.
     */
    private static void convertPageToXf(Document doc, Element contentEl, Element rootEl, String projectName) {
     log.info("🔁 Converting structure from PAGE → XF");

        // Update resource type
        contentEl.setAttribute(ATTR_SLING_RESOURCE_TYPE, projectName + COMPONENT_XFPAGE_PATH);
        // Remove header + container nodes
        NodeList children = rootEl.getChildNodes();
        List<Node> toRemove = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String name = node.getNodeName();
                if (EXPERIENCE_HEADER.equals(name) || CONTAINER_NODE.equals(name)) {
                    toRemove.add(node);
                }
            }
        }

        for (Node node : toRemove) {
            rootEl.removeChild(node);
           log.info("[convertPageToXf]:   Removed node:{} " , node.getNodeName());
        }

        // Ensure root attributes are correct
        rootEl.setAttribute(EDITABLE, EDITABLE_VALUE);
    }

    private static void setcqTempatepath( Element contentEl,String projectName,String templateName){
        String newTemplatePath = CONF_PATH + projectName + TEMPLATES_SUBPATH  + templateName;
        contentEl.setAttribute(ATTR_TEMPLATE, newTemplatePath);
    }
    /**
     * Converts structure from XF → Page type.
     * Adds experiencefragment-header and inner container nodes.
     */
    private static void convertXfToPage(Document doc, Element contentEl, Element rootEl, String projectName) {
        log.info("[convertXfToPage]: Converting structure from XF → PAGE");
        rootEl.removeAttribute(EDITABLE);
        // Update resource type
        contentEl.setAttribute(ATTR_SLING_RESOURCE_TYPE, projectName + COMPONENT_PAGE);
        // Remove existing children
        NodeList children = rootEl.getChildNodes();
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                toRemove.add(node);
            }
        }
        for (Node n : toRemove) {
            rootEl.removeChild(n);
        }

        // Add <experiencefragment-header>

        Element xfHeader = doc.createElement(EXPERIENCE_HEADER);
        xfHeader.setAttribute(ATTR_JCR_PRIMARY_TYPE, NT_UNSTRUCTURED);
        xfHeader.setAttribute(ATTR_SLING_RESOURCE_TYPE, projectName + EXPERIENCEFRAGMENT_PATH);
        xfHeader.setAttribute(FRAG_VAR,
                EXP_FRAGMENT + projectName + LANGUAGE_MASTER);
        rootEl.appendChild(xfHeader);

        // Add <container>
        Element containerEl = doc.createElement(CONTAINER_NODE);
        containerEl.setAttribute(ATTR_JCR_PRIMARY_TYPE, NT_UNSTRUCTURED);
        containerEl.setAttribute(ATTR_SLING_RESOURCE_TYPE, projectName + CONTAINER_PATH);
        containerEl.setAttribute(EDITABLE, EDITABLE_VALUE);
        containerEl.setAttribute(LAYOUT, LAYOUT_VALUE);
        rootEl.appendChild(containerEl);
    }

    /**
     * Saves XML document to file with proper indentation.
     */
    public static void saveXml(Document doc, File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        // ✅ Ensure jcr:root has all AEM-required namespaces
        Element root = doc.getDocumentElement();
        if (!root.hasAttribute(ATT_XMLNS_SLING))
            root.setAttribute(ATT_XMLNS_SLING, ATT_XMLNS_SLING_VALUE);
        if (!root.hasAttribute(ATT_XMLNS_CQ))
            root.setAttribute(ATT_XMLNS_CQ, ATT_XMLNS_CQ_VALUE);
        if (!root.hasAttribute(ATT_XMLNS_JCR))
            root.setAttribute(ATT_XMLNS_JCR, ATT_XMLNS_JCR_VALUE);
        if (!root.hasAttribute(ATT_XMLNS_NT))
            root.setAttribute(ATT_XMLNS_NT, ATT_XMLNS_NT_VALUE);

        // ✅ Always use FileOutputStream
        try (FileOutputStream fos = new FileOutputStream(file)) {
            transformer.transform(new DOMSource(doc), new StreamResult(fos));
        }

        log.info("[saveXml]: XML structure updated successfully at: {}", file.getAbsolutePath());
    }



}