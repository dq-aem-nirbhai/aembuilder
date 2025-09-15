package com.aem.builder.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public final class ConfigUtil {

    private static JsonNode policyConfig;

    private ConfigUtil() {}

    public static JsonNode getPolicyConfig() {
        if (policyConfig == null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                policyConfig = mapper.readTree(new File("src/main/resources/policy-config.json"));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load policy-config.json", e);
            }
        }
        return policyConfig;
    }
}
