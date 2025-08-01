package com.aem.builder.service;

import com.aem.builder.model.ComponentPolicy;
import com.aem.builder.model.StylePolicy;
import java.util.List;

public interface PolicyService {
    List<ComponentPolicy> getPolicies(String projectName, String componentName);
    void addPolicy(String projectName, String componentName, ComponentPolicy policy);
    default void addPolicies(String projectName, String componentName, List<ComponentPolicy> policies) {
        for (ComponentPolicy p : policies) {
            addPolicy(projectName, componentName, p);
        }
    }
}
