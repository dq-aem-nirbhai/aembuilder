package com.aem.builder.service;

import com.aem.builder.model.ComponentInfo;
import com.aem.builder.model.PolicyModel;

import java.util.List;

public interface PolicyService {

    /**
     * Fetch allowed component resource types for the template's layout container.
     */
    List<String> getAllowedComponents(String project, String template);

    /**
     * Fetch allowed components from a policy path.
     */
    List<String> getAllowedComponentsFromPolicy(String project, String policyRelPath);

    /**
     * Resolve all components in a given componentGroup.
     */
    List<String> resolveComponentsByGroup(String project, String groupName);

    /**
     * Extract the component name from its full path.
     */
    String getComponentNameOnly(String path);

    /**
     * Check if components contain design dialogs.
     */
    List<ComponentInfo> checkDesignDialogs(String project, List<String> components);

    /**
     * Save or update a policy and update template mappings.
     * Returns the policy id used for storage.
     */
    String savePolicy(String project, String template, String componentResourceType, PolicyModel policy);

    /**
     * Fetch existing policies for a given component resource type.
     */
    List<PolicyModel> getPolicies(String project, String component);

    /**
     * Load a policy by ID.
     */
    PolicyModel loadPolicy(String project, String componentResourceType, String policyId);
}
