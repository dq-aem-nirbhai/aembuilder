package com.aem.builder.controller;

import com.aem.builder.model.ComponentPolicy;
import com.aem.builder.service.PolicyService;
import com.aem.builder.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequiredArgsConstructor
public class TemplatePolicyController {
    private final TemplateService templateService;
    private final PolicyService policyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/{project}/template/{template}")
    public String allowedComponents(@PathVariable String project,
                                    @PathVariable String template,
                                    Model model) {
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("components", templateService.getAllowedComponents(project, template));
        return "template-components";
    }

    @GetMapping("/{project}/template/{template}/{component}")
    public String componentPolicies(@PathVariable String project,
                                    @PathVariable String template,
                                    @PathVariable String component,
                                    Model model) {
        model.addAttribute("projectName", project);
        model.addAttribute("templateName", template);
        model.addAttribute("componentName", component);
        model.addAttribute("policies", policyService.getPolicies(project, component));
        return "component-styles";
    }

    @PostMapping("/{project}/template/{template}/{component}/style")
    public String addPolicies(@PathVariable String project,
                              @PathVariable String template,
                              @PathVariable String component,
                              @RequestParam("policyJson") String policyJson,
                              RedirectAttributes redirectAttributes) {
        try {
            ComponentPolicy policy = objectMapper.readValue(policyJson, ComponentPolicy.class);
            policyService.addPolicy(project, component, policy);
            redirectAttributes.addFlashAttribute("message", "Style policy saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Failed to save policy");
        }
        return "redirect:/" + project + "/template/" + template + "/" + component;
    }
}
