package com.aem.builder.service;

import com.aem.builder.model.policy.PolicyModel;

import java.util.List;

public interface PolicyService {
    /**
     * Fetch allowed components for a template's root layout container.
     */
    List<String> getAllowedComponents(String projectName, String templateName);

    /**
     * Load an existing policy for the given component if available.
     */
    PolicyModel loadPolicy(String projectName, String templateName, String componentName);

    /**
     * Persist a policy for the given component.
     */
    void savePolicy(String projectName, String templateName, String componentName, PolicyModel policy);
}
