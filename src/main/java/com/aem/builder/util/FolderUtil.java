package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.File;

/**
 * Utility class for folder operations in AEM projects.
 * Provides helper methods to ensure folder structure and .content.xml files exist.
 */
@Slf4j
public final class FolderUtil {

    /**
     * Ensures the given folder exists and contains a .content.xml file.
     *
     * @param folder the folder to check/create
     */
    public static void ensureFolderContent(File folder) {
        final String methodPrefix = "ENSURE_FOLDER_CONTENT: ";

        if (folder == null) {
            log.warn("{}Folder reference is null", methodPrefix);
            return;
        }

        // Create folder if it does not exist
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                log.info("{}Created folder: {}", methodPrefix, folder.getPath());
            } else {
                log.warn("{}Failed to create folder: {}", methodPrefix, folder.getPath());
            }
        }

        File contentXml = new File(folder, ".content.xml");

        // Create .content.xml if it does not exist
        if (!contentXml.exists()) {
            try {
                Document doc = XmlUtil.newDocumentBuilder().newDocument();
                Element root = doc.createElement("jcr:root");
                root.setAttribute("xmlns:jcr", "http://www.jcp.org/jcr/1.0");
                root.setAttribute("xmlns:nt", "http://www.jcp.org/jcr/nt/1.0");
                root.setAttribute("jcr:primaryType", "nt:unstructured");
                doc.appendChild(root);

                XmlUtil.writeDoc(doc, contentXml);
                log.info("{}Created .content.xml at: {}", methodPrefix, contentXml.getPath());
            } catch (Exception e) {
                log.error("{}Failed to create .content.xml at: {}", methodPrefix, contentXml.getPath(), e);
            }
        } else {
            log.info("{}Folder already contains .content.xml: {}", methodPrefix, contentXml.getPath());
        }
    }
}
