package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class PolicyUtil {
    /**
     * Extracts the component name from a path.
     *
     * @param path full component path
     * @return the component name (last segment of path), or null/empty if input invalid
     */

    public static String getComponentNameOnly(String path) {
        final String METHOD = "GET_COMPONENT_NAME_ONLY";

        if (path == null || path.isBlank()) {
            log.info("{}: Input path is null or blank", METHOD);
            return path;
        }

        String[] parts = path.split("/");
        String componentName = parts[parts.length - 1];
        log.info("{}: Extracted component name '{}' from path '{}'", METHOD, componentName, path);
        return componentName;
    }


    /**
     * Gets a direct child element of a parent by tag name.
     *
     * @param parent parent XML element
     * @param name   tag name of the child element
     * @return child element if found, otherwise null
     */
    public static Element getChild(Element parent, String name) {
        final String METHOD = "GET_CHILD";

        if (parent == null || name == null || name.isBlank()) {
            log.info("{}: Parent element or name is null/blank", METHOD);
            return null;
        }

        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && el.getTagName().equals(name)) {
                log.info("{}: Found child element '{}'", METHOD, name);
                return el;
            }
        }

        log.info("{}: Child element '{}' not found", METHOD, name);
        return null;
    }
    /**
     * Helper: Finds a child element by name (used internally for XML manipulation)
     *
     * @param parent Parent XML element
     * @param name   Child element name
     * @param doc    Document reference (needed for node creation)
     * @return Child Element if found, else null
     */
    public static Element findChild(Element parent, String name, Document doc) {
        final String METHOD = "FIND_CHILD";

        if (parent == null || name == null || name.isBlank()) {
            log.info("{}: Parent element or child name is null/blank", METHOD);
            return null;
        }

        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
                log.info("{}: Found child element '{}'", METHOD, name);
                return (Element) node;
            }
        }

        log.info("{}: Child element '{}' not found under parent '{}'", METHOD, name, parent.getTagName());
        return null;
    }

    /**
     * Gets the current date-time in ISO format for policy metadata.
     *
     * @return Current date-time string (yyyy-MM-dd'T'HH:mm:ss.SSSXXX)
     */
    public static String getCurrentDate() {
        final String METHOD = "GET_CURRENT_DATE";

        String currentDate = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        log.info("{}: Current date-time = {}", METHOD, currentDate);
        return currentDate;
    }

    /**
     * Returns the first child element of a parent element with the given name.
     *
     * @param parent the parent XML element
     * @param name   the name of the child element to find
     * @return the child Element if found, otherwise null
     */
    public static Element getChildByName(Element parent, String name) {
        final String methodPrefix = "GET_CHILD_BY_NAME: ";
        if (parent == null || name == null || name.isEmpty()) {
            log.info("{}Parent element or name is null/empty", methodPrefix);
            return null;
        }

        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && node.getNodeName().equals(name)) {
                log.info("{}Found child element with name '{}'", methodPrefix, name);
                return (Element) node;
            }
        }
        log.info("{}No child element found with name '{}'", methodPrefix, name);
        return null;
    }


}
