package com.aem.builder.controller;

import com.aem.builder.model.*;
import com.aem.builder.service.PolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class PolicyApiController {
    private final PolicyService policyService;

    public PolicyApiController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public List<Template> listTemplates() {
        return policyService.getTemplates();
    }

    @GetMapping("/{templateId}/components")
    public List<Component> listComponents(@PathVariable String templateId) {
        return policyService.getComponents(templateId);
    }

    @GetMapping("/{templateId}/components/{componentId}/policies")
    public List<Policy> listPolicies(@PathVariable String templateId, @PathVariable String componentId) {
        return policyService.getPolicies(templateId, componentId);
    }

    @GetMapping("/{templateId}/components/{componentId}/policies/{policyId}")
    public ResponseEntity<Policy> getPolicy(@PathVariable String templateId, @PathVariable String componentId, @PathVariable String policyId) {
        Policy policy = policyService.getPolicy(templateId, componentId, policyId);
        if (policy == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(policy);
    }

    @PostMapping("/{templateId}/components/{componentId}/policies")
    public Policy createPolicy(@PathVariable String templateId, @PathVariable String componentId, @RequestBody Policy policy) {
        return policyService.savePolicy(templateId, componentId, policy);
    }

    @PutMapping("/{templateId}/components/{componentId}/policies/{policyId}")
    public ResponseEntity<Policy> updatePolicy(@PathVariable String templateId, @PathVariable String componentId, @PathVariable String policyId, @RequestBody Policy policy) {
        policy.setId(policyId);
        Policy saved = policyService.savePolicy(templateId, componentId, policy);
        if (saved == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(saved);
    }
}
