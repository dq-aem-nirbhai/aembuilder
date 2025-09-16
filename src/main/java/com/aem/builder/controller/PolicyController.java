package com.aem.builder.controller;

import com.aem.builder.model.PolicyRequest;

import com.aem.builder.service.TemplatePolicy;
import org.springframework.http.HttpStatus;

import com.aem.builder.model.ComponentInfo;
import com.aem.builder.model.PolicyModel;
import com.aem.builder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.aem.builder.constants.UrlMappings.*;
import static com.aem.builder.constants.ViewNames.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PolicyController {
    private final PolicyService policyService;
    private final TemplatePolicy policyXmlUpdater;

    /**
     * Handles requests to add or update a policy for a template.
     * If the policy exists, it will be updated; otherwise, a new one will be created.
     */
    @PostMapping(ADD_OR_UPDATE_POLICY)
    public ResponseEntity<String> addOrUpdatePolicy(@PathVariable String projectName,
                                                    @RequestParam String templateName,
                                                    @RequestBody PolicyRequest request) {
        try {
            System.out.println(request);
            // This should create OR update the policy:
            policyXmlUpdater.saveOrUpdatePolicy(projectName, templateName, request);
            return ResponseEntity.ok("Policy saved");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving policy");
        }
    }

    /**
     * Redirects to the policy form page.
     * This page allows users to create or edit a policy for a given template.
     */
    @GetMapping(REDIRECT_POLICY_FORM)
    public String redirectToPolicyForm(@PathVariable("projectName") String projectName,
                                       @RequestParam String templateName,
                                       Model model) {
        // Pass templateName to form
        model.addAttribute("projectName", projectName);
        model.addAttribute("templateName", templateName);
        model.addAttribute("policyRequest", new PolicyRequest());
        // Show the same policy form you already have
        return POLICES; // Thymeleaf page for creating policy
    }

    /**
     * Retrieves the list of existing policy names for a project.
     */
    @GetMapping(GET_EXISTING_POLICIES)
    public ResponseEntity<List<String>> getExistingPolicies(@RequestParam String projectName) {
        try {
            List<String> policies = policyXmlUpdater.getExistingPolicies(projectName);
            return ResponseEntity.ok(policies);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    /**
     * Retrieves the details of a specific policy by its title.
     */
    @GetMapping(GET_POLICY_DETAILS)
    public ResponseEntity<PolicyRequest> getPolicyDetails(@RequestParam String projectName,
                                                          @RequestParam String policyTitle) {
        try {
            PolicyRequest policy = policyXmlUpdater.getPolicyDetails(projectName, policyTitle);
            if (policy == null)
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok(policy);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Loads the policy details for a specific component.
     * Used by frontend applications to dynamically fetch policy data.
     */
    @GetMapping(LOAD_COMPONENT_POLICY)
    @ResponseBody
    public PolicyModel loadPolicy(@PathVariable String project,
                                  @RequestParam String resource,
                                  @RequestParam String policyId) {

        return policyService.loadPolicy(project, resource, policyId);
    }

    /**
     * Shows allowed components for a template.
     */
    @GetMapping(SHOW_TEMPLATE_COMPONENTS )
    public String showComponents(@PathVariable String project,
                                 @PathVariable String template,
                                 Model model) {
        List<String> components = policyService.getAllowedComponents(project, template);
        List<ComponentInfo> componentInfos = policyService.checkDesignDialogs(project, components);
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("components", components);
        model.addAttribute("components", componentInfos); // updated
        return TEMPLATE_COMPONENTS;
    }

    /**
     * Shows policy editor for component.
     */
    @GetMapping(SHOW_POLICY_EDITOR)
    public String showPolicyEditor(@PathVariable String project,
                                   @PathVariable String template,
                                   @RequestParam("resource") String component,
                                   Model model) {
        List<PolicyModel> policies = policyService.getPolicies(project, component);
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("component", component);
        model.addAttribute("policies", policies);
        return POLICY_EDITOR;
    }

    /**
     * Saves the policy settings for a component.
     * This is typically called via API from the frontend.
     */
    @PostMapping(SAVE_COMPONENT_POLICY)
    @ResponseBody
    public ResponseEntity<String> savePolicy(@PathVariable String project,
                                             @PathVariable String template,
                                             @RequestParam String resource,
                                             @RequestBody PolicyModel policy) {

        String id = policyService.savePolicy(project, template, resource, policy);
        return ResponseEntity.ok(id);
    }
}