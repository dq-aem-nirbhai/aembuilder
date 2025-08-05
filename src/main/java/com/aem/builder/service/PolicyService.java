package com.aem.builder.service;

import com.aem.builder.model.policy.PolicyModel;
import java.util.List;
import java.util.Map;

public interface PolicyService {
    /**
     * Fetch allowed component resource types for the template's layout container.
     */
    List<String> getAllowedComponents(String project, String template);

    /**
     * Fetch existing policies for a given component resource type.
     * Key of map is policy id (folder name).
     */
    Map<String, PolicyModel> getPolicies(String project, String componentResourceType);

    /**
     * Save or update a policy and update template mappings.
     * Returns the policy id used for storage.
     */
    String savePolicy(String project, String template, String componentResourceType, PolicyModel policy);

    /**
     * Load a policy by id.
     */
    PolicyModel loadPolicy(String project, String componentResourceType, String policyId);
}
