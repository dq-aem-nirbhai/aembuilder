package com.aem.builder.service;

import com.aem.builder.model.StylePolicy;
import java.util.List;

public interface PolicyService {
    List<StylePolicy> getPolicies(String projectName, String componentName);
    void addPolicy(String projectName, String componentName, StylePolicy policy);
    default void addPolicies(String projectName, String componentName, List<StylePolicy> policies) {
        for (StylePolicy p : policies) {
            addPolicy(projectName, componentName, p);
        }
    }
}
