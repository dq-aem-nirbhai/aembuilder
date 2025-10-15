package com.aem.builder.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Configuration class to load Maven command templates from a JSON file.
 * The JSON file should be located in resources and contain a key "commandTemplate".
 * This class logs important steps during initialization for easier debugging.
 */
@Slf4j
@Component
public class MavenCommandConfig {

    // Stores the Maven command template loaded from JSON
    private final String commandTemplate;

    /**
     * Constructor: Loads the Maven command template from "maven-command.json" in resources.
     *
     * @throws IOException if the JSON file is missing or cannot be read
     */
    public MavenCommandConfig() throws IOException {
        log.info("[MavenCommandConfig] Initializing MavenCommandConfig...");

        ObjectMapper mapper = new ObjectMapper();

        // Load the JSON file from the classpath
        try (InputStream is = getClass().getResourceAsStream("/maven-command.json")) {

            if (is == null) {
                // Log error if file is missing and throw exception
                log.error("[MavenCommandConfig] Failed to find maven-command.json in resources");
                throw new IOException("maven-command.json not found in resources");
            }

            log.info("[MavenCommandConfig] Found maven-command.json, reading content...");

            // Parse JSON into a Map
            Map<String, String> map = mapper.readValue(is, Map.class);

            // Retrieve commandTemplate key
            if (map.containsKey("commandTemplate")) {
                this.commandTemplate = map.get("commandTemplate");
                log.info("[MavenCommandConfig] Loaded commandTemplate successfully: {}", commandTemplate);
            } else {
                // Warn if the key is missing
                log.warn("[MavenCommandConfig] commandTemplate key not found in JSON, setting as null");
                this.commandTemplate = null;
            }

        } catch (IOException e) {
            // Log full stack trace if reading fails
            log.error("[MavenCommandConfig] Error while reading maven-command.json", e);
            throw e;
        }
    }

    /**
     * Returns the loaded Maven command template.
     *
     * @return commandTemplate string from JSON, or null if not found
     */
    public String getCommandTemplate() {
        log.debug("[MavenCommandConfig] Returning commandTemplate: {}", commandTemplate);
        return commandTemplate;
    }
}
