package com.aem.builder.controller;

import com.aem.builder.model.PolicyModel;
import com.aem.builder.service.ComponentService;
import com.aem.builder.service.PolicyService;
import com.aem.builder.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{projectName}/policy")
public class PolicyRestController {

    private final TemplateService templateService;
    private final ComponentService componentService;
    private final PolicyService policyService;

    @GetMapping("/templates")
    public List<String> listTemplates(@PathVariable String projectName) {
        return templateService.getTemplateNamesFromDestination(projectName);
    }

    @GetMapping("/{template}/components")
    public List<String> listComponents(@PathVariable String projectName, @PathVariable String template) throws IOException {
        return componentService.getAllComponents();
    }

    @GetMapping("/{template}/{component}")
    public PolicyModel getPolicy(@PathVariable String projectName,
                                 @PathVariable String template,
                                 @PathVariable String component) {
        return policyService.getPolicy(projectName, template, component);
    }

    @PostMapping("/{template}/{component}")
    public ResponseEntity<String> savePolicy(@PathVariable String projectName,
                                             @PathVariable String template,
                                             @PathVariable String component,
                                             @RequestBody PolicyModel policy) {
        policyService.savePolicy(projectName, template, component, policy);
        return ResponseEntity.ok("Policy saved");
    }
}
