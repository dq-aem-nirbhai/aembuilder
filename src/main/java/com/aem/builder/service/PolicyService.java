package com.aem.builder.service;

import com.aem.builder.model.PolicyModel;

public interface PolicyService {
    PolicyModel getPolicy(String project, String template, String component);
    void savePolicy(String project, String template, String component, PolicyModel policy);
}
