package com.aem.builder.util;


import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.StringWriter;


import static com.aem.builder.constants.XmlConstants.METHOD_SAFE_NODE_NAME;
import static com.aem.builder.constants.XmlConstants.NODE_NAME_REGEX;


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
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        String rawXml = writer.toString();
        String formattedXml = formatXml(rawXml);
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(formattedXml);
        }
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

    /**
     * Recursively prints an XML element and its children into a formatted string.
     * <p>
     * This method handles:
     * <ul>
     *   <li>Indentation for nested elements</li>
     *   <li>Escaping of attribute values and text content</li>
     *   <li>Self-closing tags if there are no children</li>
     * </ul>
     *
     * @param element the DOM {@link Element} to format
     * @param sb      the {@link StringBuilder} where formatted XML is appended
     * @param indent  the current indentation level (0 for root)
     */
    private static void printElement(Element element, StringBuilder sb, int indent) {
        final String methodPrefix = "PRINT_ELEMENT: ";
        if (element == null) {
            log.warn("{}Provided element is null, skipping", methodPrefix);
            return;
        }
        String indentStr = "    ".repeat(indent);
        String tagName = element.getTagName();
        log.debug("{}Processing element <{}> at indent {}", methodPrefix, tagName, indent);
        sb.append(indentStr).append("<").append(tagName);
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            log.trace("{}Attribute found: {}=\"{}\"", methodPrefix, attr.getNodeName(), attr.getNodeValue());
            sb.append("\n")
                    .append(indentStr).append("    ")
                    .append(attr.getNodeName())
                    .append("=\"")
                    .append(escapeXml(attr.getNodeValue()))
                    .append("\"");
        }
        NodeList children = element.getChildNodes();
        if (children.getLength() == 0) {
            log.debug("{}Element <{}> has no children, using self-closing tag", methodPrefix, tagName);
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
                        log.trace("{}Text node in <{}>: {}", methodPrefix, tagName, text);
                        sb.append(indentStr).append("    ")
                                .append(escapeXml(text))
                                .append("\n");
                    }
                }
            }
            sb.append(indentStr).append("</").append(tagName).append(">\n");
            log.debug("{}Closed element </{}>", methodPrefix, tagName);
        }
    }

    /**
     * Escapes XML special characters in attribute values or text content.
     *
     * @param value raw string
     * @return escaped XML-safe string
     */
    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

}
