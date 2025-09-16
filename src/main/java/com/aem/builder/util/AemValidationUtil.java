package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class AemValidationUtil {

    /** Check if the folder contains AEM structure */
    public static boolean hasAemStructure(Path repoRoot) {
        boolean structureExists = Files.isDirectory(repoRoot.resolve("core"))
                && Files.isDirectory(repoRoot.resolve("ui.apps"))
                && Files.isDirectory(repoRoot.resolve("ui.content"));
        if (structureExists) log.info("[AemValidationUtil] Detected standard AEM folder structure in '{}'", repoRoot);
        return structureExists;
    }

    /** Checks if pom.xml contains AEM-specific packaging or dependencies */
    public static boolean isAemModulePom(Path pomPath) {
        try {
            String xml = Files.readString(pomPath);
            if (xml.contains("<packaging>bundle</packaging>")
                    || xml.contains("<packaging>content-package</packaging>")
                    || xml.contains("<packaging>all</packaging>")
                    || xml.contains("filevault-package-maven-plugin")
                    || xml.contains("content-package-maven-plugin")
                    || xml.contains("com.day.jcr.vault")
                    || xml.contains("com.adobe.cq")) {
                return true;
            }
        } catch (IOException e) {
            log.warn("[AemValidationUtil] Failed to read pom.xml at '{}': {}", pomPath, e.getMessage());
        }
        return false;
    }

    /** Filter out junk paths like .git, target, node_modules */
    public static boolean isNotJunk(Path path) {
        String p = path.toString().toLowerCase();
        boolean result = !(p.contains(".git") || p.contains("target") || p.contains("node_modules"));
        if (!result) log.debug("[AemValidationUtil] Skipping junk path '{}'", path);
        return result;
    }
}
