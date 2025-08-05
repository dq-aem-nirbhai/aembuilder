package com.aem.builder.controller;

import com.aem.builder.model.policy.PolicyModel;
import com.aem.builder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    /**
     * Shows allowed components for a template.
     */
    @GetMapping("/{project}/templates/{template}/components")
    public String showComponents(@PathVariable String project,
                                 @PathVariable String template,
                                 Model model) {
        List<String> components = policyService.getAllowedComponents(project, template);
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("components", components);
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
        Map<String, PolicyModel> policies = policyService.getPolicies(project, component);
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("component", component);
        model.addAttribute("policies", policies);
        return "policy-editor";
    }

    @GetMapping("/api/{project}/component/policy")
    @ResponseBody
    public PolicyModel loadPolicy(@PathVariable String project,
                                  @RequestParam String resource,
                                  @RequestParam String policyId) {
        return policyService.loadPolicy(project, resource, policyId);
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
