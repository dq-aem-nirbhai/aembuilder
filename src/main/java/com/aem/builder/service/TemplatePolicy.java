package com.aem.builder.service;

import java.util.Map;

public interface TemplatePolicy {
    public String addPolicy(String projectname,String policyName, String componentGroups,String styleDefaultClasses,
                            String styleDefaultElement, Map<String, Map<String, String>> styles) throws Exception ;
    public void assignPolicyToTemplate(String projectName, String templateName,
                                       String policyNodeName) throws Exception;
    }
