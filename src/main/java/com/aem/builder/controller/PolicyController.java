package com.aem.builder.controller;

import com.aem.builder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/policy")
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping("/{project}/{template}/components")
    public ResponseEntity<List<String>> getAllowedComponents(@PathVariable String project,
                                                             @PathVariable String template) {
        List<String> comps = policyService.getAllowedComponents(project, template);
        return ResponseEntity.ok(comps);
    }

    @GetMapping("/{project}/{template}/map")
    public ResponseEntity<Map<String, String>> getPolicyMap(@PathVariable String project,
                                                            @PathVariable String template) {
        Map<String, String> map = policyService.getComponentPolicies(project, template);
        return ResponseEntity.ok(map);
    }
}
