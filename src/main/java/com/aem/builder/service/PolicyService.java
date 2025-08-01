package com.aem.builder.service;

import java.util.List;
import java.util.Map;

public interface PolicyService {
    /**
     * Return a list of component names allowed in the given template.
     */
    List<String> getAllowedComponents(String projectName, String templateName);

    /**
     * Return mapping of component name to policy path for the given template.
     */
    Map<String, String> getComponentPolicies(String projectName, String templateName);
}
