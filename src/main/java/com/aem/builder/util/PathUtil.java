package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for AEM path and resource operations.
 */
@Slf4j
public final class PathUtil {

    /**
     * Extracts the resource type from an absolute AEM component path.
     * Example: "/apps/myproject/components/button" -> "myproject/components/button"
     *
     * @param absolutePath the absolute path
     * @return the resource type, or null if not found
     */
    public static String getResourceTypeFromPath(String absolutePath) {
        final String methodPrefix = "GET_RESOURCE_TYPE_FROM_PATH: ";

        if (absolutePath == null || absolutePath.isEmpty()) {
            log.warn("{}Input path is null or empty", methodPrefix);
            return null;
        }

        // Normalize slashes
        String normalized = absolutePath.replace("\\", "/");
        int appsIndex = normalized.indexOf("/apps/");
        if (appsIndex == -1) {
            log.info("{}Path does not contain '/apps/': {}", methodPrefix, absolutePath);
            return null;
        }

        String resPath = normalized.substring(appsIndex + 6); // skip "/apps/"
        log.info("{}Extracted resource type: {}", methodPrefix, resPath);
        return resPath;
    }
}
