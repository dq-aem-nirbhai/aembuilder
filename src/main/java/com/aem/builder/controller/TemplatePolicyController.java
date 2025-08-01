package com.aem.builder.controller;

import com.aem.builder.model.StylePolicy;
import com.aem.builder.service.PolicyService;
import com.aem.builder.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class TemplatePolicyController {
    private final TemplateService templateService;
    private final PolicyService policyService;

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
                              @RequestParam("name") List<String> names,
                              @RequestParam("cssClass") List<String> classes,
                              RedirectAttributes redirectAttributes) {
        List<StylePolicy> list = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            String c = i < classes.size() ? classes.get(i) : "";
            if (!n.isBlank() && !c.isBlank()) {
                list.add(new StylePolicy(n, c));
            }
        }
        policyService.addPolicies(project, component, list);
        redirectAttributes.addFlashAttribute("message", "Style policies saved");
        return "redirect:/" + project + "/template/" + template + "/" + component;
    }
}
