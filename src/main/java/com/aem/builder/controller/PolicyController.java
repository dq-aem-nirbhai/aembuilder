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
@Slf4j
@Controller
public class PolicyController {
    private final PolicyService policyService;
    private final TemplatePolicy policyXmlUpdater;

    public PolicyController(PolicyService policyService, TemplatePolicy policyXmlUpdater) {
        this.policyService = policyService;
        this.policyXmlUpdater = policyXmlUpdater;
    }

    @PostMapping("/policies/add/{projectName}")
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

    @GetMapping("/{projectName}/addpolicy")
    public String redirectToPolicyForm(@PathVariable("projectName") String projectName,
            @RequestParam String templateName,
            Model model) throws Exception {
        // Pass templateName to form
        model.addAttribute("projectName", projectName);
        model.addAttribute("templateName", templateName);
        model.addAttribute("policyRequest", new PolicyRequest());
        // ⚠️ missing existingPolicies, but template expects it
        List<String> existingPolicies = policyXmlUpdater.getExistingPolicies(projectName);
        model.addAttribute("existingPolicies", existingPolicies);
        // Show the same policy form you already have
        return "policies"; // Thymeleaf page for creating policy
    }

    public String addPolicyToParticularTemplate() {
        return "";
    }



    @GetMapping("/get-existing-policies")
    public ResponseEntity<List<String>> getExistingPolicies(@RequestParam String projectName) {
        try {
            List<String> policies = policyXmlUpdater.getExistingPolicies(projectName);
            return ResponseEntity.ok(policies);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @GetMapping("/get-policy-details")
    public ResponseEntity<PolicyRequest> getPolicyDetails(@RequestParam String projectName,
            @RequestParam String policyTitle) {
        try {
            PolicyRequest policy = policyXmlUpdater.getPolicyDetails(projectName, policyTitle);
            System.out.println(policy.toString());
            if (policy == null)
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok(policy);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/api/{project}/component/policy")
    @ResponseBody
    public PolicyModel loadPolicy(@PathVariable String project,
            @RequestParam String resource,
            @RequestParam String policyId) {

        log.info("......{}", policyService.loadPolicy(project, resource, policyId));
        return policyService.loadPolicy(project, resource, policyId);
    }



    /**
     * Shows allowed components for a template.
     */
    @GetMapping("/{project}/templates/{template}/components")
    public String showComponents(@PathVariable String project,
            @PathVariable String template,
            Model model) {
        List<String> components = policyService.getAllowedComponents(project, template);
        List<ComponentInfo> componentInfos = policyService.checkDesignDialogs(project, components);
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("components", components);
        model.addAttribute("components", componentInfos); // updated
        return "template-components";
    }

    /**
     * Shows policy editor for component.
     */
    @GetMapping("/{project}/templates/{template}/component")
    public String showPolicyEditor(@PathVariable String project,
            @PathVariable String template,
            @RequestParam("resource") String component,
            Model model) {
        List<PolicyModel> policies = policyService.getPolicies(project, component);
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("component", component);
        model.addAttribute("policies", policies);
        return "policy-editor";
    }

    @PostMapping("/api/{project}/templates/{template}/component/policy")
    @ResponseBody
    public ResponseEntity<String> savePolicy(@PathVariable String project,
            @PathVariable String template,
            @RequestParam String resource,
            @RequestBody PolicyModel policy) {

        String id = policyService.savePolicy(project, template, resource, policy);
        return ResponseEntity.ok(id);
    }
}
