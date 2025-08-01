package com.aem.builder.controller.policy;

import com.aem.builder.model.policy.PolicyModel;
import com.aem.builder.service.policy.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policy")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping("/{project}/{template}/components")
    public List<String> getComponents(@PathVariable String project, @PathVariable String template) {
        return policyService.getAllowedComponents(project, template);
    }

    @GetMapping("/{project}/component/{component}")
    public List<String> getPolicies(@PathVariable String project, @PathVariable String component) {
        return policyService.getPoliciesForComponent(project, component);
    }

    @GetMapping("/{project}/component/{component}/{policy}")
    public PolicyModel loadPolicy(@PathVariable String project, @PathVariable String component, @PathVariable String policy) {
        return policyService.readPolicy(project, component, policy);
    }

    @PostMapping("/{project}/{template}/component/{component}")
    public ResponseEntity<Void> savePolicy(@PathVariable String project,
                                           @PathVariable String template,
                                           @PathVariable String component,
                                           @RequestBody PolicyModel model) {
        policyService.savePolicy(project, template, component, model);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{project}/{template}/component/{component}/{policy}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String project,
                                             @PathVariable String template,
                                             @PathVariable String component,
                                             @PathVariable String policy) {
        policyService.deletePolicy(project, template, component, policy);
        return ResponseEntity.ok().build();
    }
}
