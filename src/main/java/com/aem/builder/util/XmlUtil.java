package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

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

}
