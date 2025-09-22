package com.aem.builder.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;

/**
 * Utility class to load JSON configuration files from the classpath.
 * Used for reading template/config JSON (like slingmodel.json).
 */
public final class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Loads a JSON file from the classpath and converts it to a Map.
     *
     * @param fileName JSON file name (must be in resources folder)
     * @return JSON contents as a Map
     * @throws Exception if the file cannot be found or parsed
     */

    public static Map<String, Object> loadConfig(String fileName) throws Exception {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in == null) {
                throw new IllegalArgumentException("Configuration file not found: " + fileName);
            }
            return MAPPER.readValue(in, Map.class);
        }
    }
}
