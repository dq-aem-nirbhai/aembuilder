package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;

import static com.aem.builder.constants.XmlConstants.*;

/**
 * Utility class for XML-related operations.
 */
@Slf4j
public final class XmlUtil {


    /**
     * Creates a new DocumentBuilder instance.
     *
     * @return DocumentBuilder instance
     */
    public static DocumentBuilder newDocumentBuilder() {
        try {
            log.info("NEW_DOCUMENT_BUILDER: Creating new DocumentBuilder");
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.error("NEW_DOCUMENT_BUILDER: Error creating DocumentBuilder", e);
            // Wrap and rethrow as RuntimeException
            throw new RuntimeException("Failed to create DocumentBuilder", e);
        }
    }

    /**
     * Returns a child element by name if it exists, otherwise creates it.
     * The new element is set with jcr:primaryType="nt:unstructured".
     *
     * @param doc       the XML Document
     * @param parent    the parent element
     * @param childName the name of the child element
     * @return the existing or newly created child element
     */
    public static Element getOrCreateChild(Document doc, Element parent, String childName) {
        final String methodPrefix = "GET_OR_CREATE_CHILD: ";

        if (doc == null || parent == null || childName == null || childName.isEmpty()) {
            log.warn("{}Document, parent element, or child name is null/empty", methodPrefix);
            return null;
        }

        NodeList children = parent.getElementsByTagName(childName);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(childName)) {
                log.info("{}Found existing child element '{}'", methodPrefix, childName);
                return (Element) child;
            }
        }

        // Create new child element if not found
        Element newChild = doc.createElement(childName);
        newChild.setAttribute("jcr:primaryType", "nt:unstructured");
        parent.appendChild(newChild);
        log.info("{}Created new child element '{}'", methodPrefix, childName);
        return newChild;
    }


    /**
     * Writes a DOM Document to a file with indentation.
     *
     * @param doc  the DOM Document to write
     * @param file the target file
     * @throws Exception if any error occurs during writing
     */
    public static void writeDoc(Document doc, File file) throws Exception {
        final String methodPrefix = "WRITE_DOC: ";
        if (doc == null || file == null) {
            log.warn("{}Document or file is null", methodPrefix);
            return;
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);

        log.info("{}XML document written to {}", methodPrefix, file.getAbsolutePath());
    }

    /**
     * Formats a raw XML string into a properly indented XML.
     *
     * @param xml Raw XML string
     * @return Formatted XML string
     */
    public static String formatXml(String xml) {
        if (xml == null || xml.isBlank()) return "";

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringElementContentWhitespace(true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.normalizeDocument();

            StringBuilder sb = new StringBuilder();
            printElement(doc.getDocumentElement(), sb, 0);
            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return xml;
        }
    }

    private static void printElement(Element element, StringBuilder sb, int indent) {
        String indentStr = "    ".repeat(indent);

        // opening tag start
        sb.append(indentStr).append("<").append(element.getTagName());

        // attributes on new lines with +4 spaces
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            sb.append("\n")
                    .append(indentStr).append("    ")
                    .append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\"");
        }

        NodeList children = element.getChildNodes();
        boolean hasChildElements = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                hasChildElements = true;
                break;
            }
        }

        if (children.getLength() == 0) {
            // self closing tag
            sb.append("/>\n");
        } else {
            sb.append(">\n");
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    printElement((Element) child, sb, indent + 1);
                } else if (child instanceof Text) {
                    String text = ((Text) child).getWholeText().trim();
                    if (!text.isEmpty()) {
                        sb.append(indentStr).append("    ").append(text).append("\n");
                    }
                }
            }
            sb.append(indentStr).append("</").append(element.getTagName()).append(">\n");
        }
    }


    /**
     * Sanitizes field name for safe XML node naming.
     *
     * @param fieldName Raw field name
     * @param fallback  Fallback name if invalid
     * @return Sanitized safe XML node name
     */
    public static String safeNodeName(String fieldName, String fallback) {
        log.info("{}: Sanitizing fieldName='{}' with fallback='{}'", METHOD_SAFE_NODE_NAME, fieldName, fallback);

        if (fieldName == null || fieldName.isBlank()) {
            log.info("{}: Field name is null/blank, using fallback '{}'", METHOD_SAFE_NODE_NAME, fallback);
            return fallback;
        }

        String sanitized = fieldName.replaceAll(NODE_NAME_REGEX, "");

        if (sanitized.isEmpty()) {
            log.info("{}: Sanitized name is empty, using fallback '{}'", METHOD_SAFE_NODE_NAME, fallback);
            return fallback;
        }

        log.info("{}: Returning sanitized name '{}'", METHOD_SAFE_NODE_NAME, sanitized);
        return sanitized;
    }


}
