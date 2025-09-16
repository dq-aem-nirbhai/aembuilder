package com.aem.builder.service;

import com.aem.builder.model.PolicyRequest;

import java.util.List;
import java.util.Map;

public interface TemplatePolicy {
    /**
     * Adds a new policy node to the policies XML file.
     */
    public String addPolicy(String projectname, String policyName, String componentGroups,
                            String styleDefaultClasses, String styleDefaultElement,
                            Map<String, Map<String, Object>> styles)  throws Exception  ;
    /**
     * Assigns a policy to a template by updating the template's .content.xml file.
     * Logs the action and template type, then calls TemplateUtil to update the policy reference.
     */
    public void assignPolicyToTemplate(String projectName, String templateName,
                                       String policyNodeName) throws Exception;
    /**
     * Saves a new policy or updates an existing one in the policies XML file.
     */
    public void saveOrUpdatePolicy(String projectName, String templateName, PolicyRequest request) throws Exception;

    /**
     * Retrieves the list of existing policy names from the XML file.
     */
    List<String> getExistingPolicies(String projectName) throws Exception;

    /**
     * Retrieves the details of a specific policy from the XML file.
     */
    PolicyRequest getPolicyDetails(String projectName, String policyTitle) throws Exception;
    }
