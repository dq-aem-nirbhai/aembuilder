package com.aem.builder.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to load and provide policy-config.json as a JsonNode.
 * Loads the file lazily and caches it for subsequent access.
 * Throws RuntimeException if the file cannot be read.
 */
public final class ConfigUtil {

    private static final Logger log = LoggerFactory.getLogger(ConfigUtil.class);

    /** Cached JsonNode of policy-config.json */
    private static JsonNode policyConfig;

    /**
     * Returns the parsed JsonNode of policy-config.json, loading it once if needed.
     * Logs success or failure of file loading using method name as prefix.
     */
    public static JsonNode getPolicyConfig() {
        final String methodPrefix = "getPolicyConfig: "; // Prefix for log messages

        // Lazy initialization: load JSON only once
        if (policyConfig == null) {
            try {
                ObjectMapper mapper = new ObjectMapper();

                // Read JSON file from resources
                policyConfig = mapper.readTree(new File("src/main/resources/policy-config.json"));
                log.info("{}policy-config.json loaded successfully.", methodPrefix);
            } catch (IOException e) {
                log.error("{}Failed to load policy-config.json", methodPrefix, e);
                throw new RuntimeException("Failed to load policy-config.json", e);
            }
        } else {
            log.debug("{}Returning cached policy-config.json", methodPrefix);
        }

        // Return cached JSON configuration
        return policyConfig;
    }
}
