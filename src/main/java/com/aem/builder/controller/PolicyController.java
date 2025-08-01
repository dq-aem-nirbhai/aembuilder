package com.aem.builder.controller;

import com.aem.builder.model.PolicyModel;
import com.aem.builder.service.PolicyService;
import com.aem.builder.service.ComponentService;
import com.aem.builder.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final ComponentService componentService;
    private final TemplateService templateService;

    @GetMapping("/{project}/createpolicy")
    public String showCreatePolicy(@PathVariable String project, Model model) {
        model.addAttribute("projectName", project);
        model.addAttribute("components", componentService.fetchComponentsFromGeneratedProjects(project));
        model.addAttribute("templates", templateService.fetchTemplatesFromGeneratedProjects(project));
        return "create-policy";
    }

    @PostMapping("/policy/create/{project}/{template}")
    @ResponseBody
    public ResponseEntity<String> createPolicy(@PathVariable String project,
                                               @PathVariable String template,
                                               @RequestBody PolicyModel model) {
        try {
            model.setTemplateName(template);
            policyService.createPolicy(project, model);
            return ResponseEntity.ok("Policy created successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to create policy");
        }
    }
}
