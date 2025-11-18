package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class PomXmlUtil {

    /**
     * Read the <artifactId> from a pom.xml file
     * @param pomFile Path to pom.xml
     * @return artifactId string or null if not found
     * @throws IOException on read errors
     */
    public static String readArtifactId(Path pomFile) throws IOException {
        try (InputStream is = Files.newInputStream(pomFile)) {
            Document doc = buildDocument(is);
            NodeList nodes = doc.getElementsByTagName("artifactId");
            if (nodes.getLength() > 0) {
                String artifactId = nodes.item(0).getTextContent();
                log.info("[readArtifactId] Read artifactId '{}' from '{}'", artifactId, pomFile);
                return artifactId;
            }
            log.warn("[readArtifactId] No <artifactId> found in '{}'", pomFile);
            return null;
        } catch (Exception e) {
            log.error("[readArtifactId] Failed to read artifactId from '{}': {}", pomFile, e.getMessage(), e);
            throw new IOException("Failed to read artifactId from pom.xml at " + pomFile, e);
        }
    }

    /**
     * Parse <artifactId> from an InputStream of pom.xml
     * @param pomStream InputStream of pom.xml
     * @return artifactId string or null
     */
    public static String parseArtifactId(InputStream pomStream) {
        try {
            Document doc = buildDocument(pomStream);
            NodeList nodes = doc.getElementsByTagName("artifactId");
            if (nodes.getLength() > 0) {
                String artifactId = nodes.item(0).getTextContent();
                log.info("[parseArtifactId] Parsed artifactId '{}'", artifactId);
                return artifactId;
            }
            log.warn("[parseArtifactId] No <artifactId> found in pom.xml");
            return null;
        } catch (Exception e) {
            log.error("[parseArtifactId] Failed to parse pom.xml: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Update or add a timestamp property in <properties> of pom.xml
     * @param pomFile Path to pom.xml
     * @param propertyName Property name to update or add
     * @param toRemove List of property names to remove before adding/updating
     * @throws IOException on write errors
     */
    public static void updatePomProperty(Path pomFile, String propertyName, List<String> toRemove) throws IOException {
        try {
            // Load pom.xml
            Document doc;
            try (InputStream is = Files.newInputStream(pomFile)) {
                doc = buildDocument(is);
            }

            // Get or create <properties> element
            Element propertiesElement = getOrCreateProperties(doc);

            // Remove unwanted properties if any
            if (toRemove != null) removeProperties(propertiesElement, toRemove);

            // Update existing property or add new
            Element existing = getPropertyElement(propertiesElement, propertyName);
            String now = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            if (existing != null) {
                existing.setTextContent(now);
                log.info("[updatePomProperty] Updated property '{}' with '{}'", propertyName, now);
            } else {
                Element newEl = doc.createElement(propertyName);
                newEl.setTextContent(now);
                propertiesElement.appendChild(newEl);
                log.info("[updatePomProperty] Added property '{}' with '{}'", propertyName, now);
            }

            // Save changes to pom.xml
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(pomFile.toFile()));
            log.info("[updatePomProperty] pom.xml updated successfully: {}", pomFile);

        } catch (Exception e) {
            log.error("[updatePomProperty] Failed to update property '{}': {}", propertyName, e.getMessage(), e);
            throw new IOException("Failed to update pom.xml with property: " + propertyName, e);
        }
    }

    /** ----------------- Helper Methods ----------------- */

    /** Build XML Document safely from InputStream */
    private static Document buildDocument(InputStream is) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = dbFactory.newDocumentBuilder();
        Document doc = builder.parse(is);
        doc.getDocumentElement().normalize();
        return doc;
    }

    /** Get existing <properties> or create if missing */
    private static Element getOrCreateProperties(Document doc) {
        NodeList propsList = doc.getElementsByTagName("properties");
        Element propertiesElement = propsList.getLength() > 0
                ? (Element) propsList.item(0)
                : doc.createElement("properties");
        if (propsList.getLength() == 0) doc.getDocumentElement().appendChild(propertiesElement);
        return propertiesElement;
    }

    /** Remove properties listed in toRemove */
    private static void removeProperties(Element properties, List<String> toRemove) {
        for (int i = properties.getChildNodes().getLength() - 1; i >= 0; i--) {
            Node child = properties.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && toRemove.contains(child.getNodeName())) {
                properties.removeChild(child);
                log.info("[removeProperties] Removed property '{}'", child.getNodeName());
            }
        }
    }

    /** Get a property element by name */
    private static Element getPropertyElement(Element properties, String propertyName) {
        for (int i = 0; i < properties.getChildNodes().getLength(); i++) {
            Node n = properties.getChildNodes().item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && propertyName.equals(n.getNodeName())) {
                return (Element) n;
            }
        }
        return null;
    }
    public static void ensureAllDatesPresent(Path pomFile) {
        try {
            Document doc = loadDocument(pomFile);

            Element props = (Element) doc.getElementsByTagName("properties").item(0);
            if (props == null) return;

            if (props.getElementsByTagName("createdDate").getLength() == 0) {
                appendTag(doc, props, "createdDate", "Unknown");
            }
            if (props.getElementsByTagName("importDate").getLength() == 0) {
                appendTag(doc, props, "importDate", "Unknown");
            }
            if (props.getElementsByTagName("cloneDate").getLength() == 0) {
                appendTag(doc, props, "cloneDate", "Unknown");
            }

            saveDocument(doc, pomFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Document loadDocument(Path filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(filePath.toFile());
    }
    public static void saveDocument(Document doc, Path filePath) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(filePath.toFile());

        transformer.transform(source, result);
    }
    public static void appendTag(Document doc, Element parent, String tagName, String value) {
        Element newTag = doc.createElement(tagName);
        newTag.setTextContent(value);
        parent.appendChild(newTag);
    }

}
