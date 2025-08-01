package com.aem.builder.controller;

import com.aem.builder.model.PolicyModel;
import com.aem.builder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping("/policy/create/{project}/{template}")
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
