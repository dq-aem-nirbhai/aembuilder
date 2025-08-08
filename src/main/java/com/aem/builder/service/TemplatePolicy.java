package com.aem.builder.service;

import com.aem.builder.model.PolicyRequest;

import javax.xml.parsers.ParserConfigurationException;
import java.util.List;
import java.util.Map;

public interface TemplatePolicy {
    public String addPolicy(String projectname,String policyName, String componentGroups,String styleDefaultClasses,
                            String styleDefaultElement, Map<String, Map<String, String>> styles) throws Exception ;
    public void assignPolicyToTemplate(String projectName, String templateName,
                                       String policyNodeName) throws Exception;

    public void saveOrUpdatePolicy(String projectName, String templateName, PolicyRequest request) throws Exception;
    List<String> getExistingPolicies(String projectName) throws Exception;

    PolicyRequest getPolicyDetails(String projectName, String policyTitle) throws Exception;
    }
