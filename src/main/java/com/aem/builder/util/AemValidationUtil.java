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

}
