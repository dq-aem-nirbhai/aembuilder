package com.aem.builder.service;

import com.aem.builder.model.PolicyModel;

import java.io.IOException;
import java.util.List;

public interface PolicyService {
    List<String> getAllowedComponents(String projectName, String templateName);
    void createPolicy(String projectName, String templateName, String componentName, PolicyModel policy) throws IOException;
}
