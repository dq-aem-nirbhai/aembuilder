package com.aem.builder.controller;

import com.aem.builder.model.PolicyModel;
import com.aem.builder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PolicyController {
    private final PolicyService policyService;

    @GetMapping("/template-components/{project}/{template}")
    @ResponseBody
    public List<String> getTemplateComponents(@PathVariable String project, @PathVariable String template) {
        return policyService.getAllowedComponents(project, template);
    }

    @GetMapping("/{project}/policy/{template}/{component}")
    public String showPolicyForm(@PathVariable String project,
                                 @PathVariable String template,
                                 @PathVariable String component,
                                 Model model) {
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("componentName", component);
        model.addAttribute("policy", new PolicyModel());
        return "policy-ui";
    }

    @PostMapping("/{project}/policy/{template}/{component}")
    public String createPolicy(@PathVariable String project,
                               @PathVariable String template,
                               @PathVariable String component,
                               @ModelAttribute PolicyModel policy,
                               RedirectAttributes redirectAttributes) {
        try {
            policyService.createPolicy(project, template, component, policy);
            redirectAttributes.addFlashAttribute("message", "Policy created successfully");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create policy");
        }
        return "redirect:/" + project;
    }
}
