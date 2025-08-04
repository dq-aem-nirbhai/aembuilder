package com.aem.builder.controller;

import com.aem.builder.model.policy.PolicyModel;
import com.aem.builder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller exposing endpoints to inspect allowed components for a template
 * and to create/update component policies. The UI mimics AEM's template and
 * policy editors but operates on the generated project structure.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/{projectName}/templates/{templateName}")
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping("/components")
    public String listAllowedComponents(@PathVariable String projectName,
                                        @PathVariable String templateName,
                                        Model model) {
        List<String> components = policyService.getAllowedComponents(projectName, templateName);
        model.addAttribute("projectName", projectName);
        model.addAttribute("templateName", templateName);
        model.addAttribute("components", components);
        return "template-components";
    }

    @GetMapping("/policies/{component}")
    @ResponseBody
    public PolicyModel loadPolicy(@PathVariable String projectName,
                                  @PathVariable String templateName,
                                  @PathVariable("component") String componentName) {
        return policyService.loadPolicy(projectName, templateName, componentName);
    }

    @PostMapping("/policies/{component}")
    @ResponseBody
    public ResponseEntity<String> savePolicy(@PathVariable String projectName,
                                             @PathVariable String templateName,
                                             @PathVariable("component") String componentName,
                                             @RequestBody PolicyModel model) {
        policyService.savePolicy(projectName, templateName, componentName, model);
        return ResponseEntity.ok("saved");
    }
}
