package com.aem.builder.service.policy;

import com.aem.builder.model.policy.PolicyModel;

import java.util.List;

public interface PolicyService {
    List<String> getAllowedComponents(String projectName, String templateName);

    List<String> getPoliciesForComponent(String projectName, String componentName);

    PolicyModel readPolicy(String projectName, String componentName, String policyName);

    void savePolicy(String projectName, String templateName, String componentName, PolicyModel policy);

    void deletePolicy(String projectName, String templateName, String componentName, String policyName);
}
