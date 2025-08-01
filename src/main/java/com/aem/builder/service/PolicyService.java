package com.aem.builder.service;

import com.aem.builder.model.PolicyModel;

public interface PolicyService {
    void createPolicy(String projectName, PolicyModel model) throws Exception;
}
