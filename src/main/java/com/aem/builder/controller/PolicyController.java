package com.aem.builder.controller;

import com.aem.builder.model.PolicyRequest;

import com.aem.builder.service.TemplatePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@Controller
public class PolicyController {

    private final TemplatePolicy policyXmlUpdater;

    public PolicyController(TemplatePolicy policyXmlUpdater) {
        this.policyXmlUpdater = policyXmlUpdater;
    }




    @GetMapping("/{projectName}/addpolicy")
    public String redirectToPolicyForm(@PathVariable("projectName")  String projectName,
                                       @RequestParam String templateName,
                                       Model model) {
        // Pass templateName to form
        model.addAttribute("projectName", projectName);
        model.addAttribute("templateName", templateName);
        model.addAttribute("policyRequest", new PolicyRequest());
        // Show the same policy form you already have
        return "policies";  // Thymeleaf page for creating policy
    }



    @GetMapping("/get-existing-policies")
    public ResponseEntity<List<String>> getExistingPolicies(@RequestParam String projectName) {
        try {
            List<String> policies = policyXmlUpdater.getExistingPolicies(projectName);
            return ResponseEntity.ok(policies);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @GetMapping("/get-policy-details")
    public ResponseEntity<PolicyRequest> getPolicyDetails(@RequestParam String projectName,
                                                          @RequestParam String policyTitle) {
        try {
            PolicyRequest policy = policyXmlUpdater.getPolicyDetails(projectName, policyTitle);
            if (policy == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(policy);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/policies/add/{projectName}")
    public ResponseEntity<String> addOrUpdatePolicy(@PathVariable String projectName,
                                                    @RequestParam String templateName,
                                                    @RequestBody PolicyRequest request) {
        try {
            System.out.println(request);
            // This should create OR update the policy:
            policyXmlUpdater.saveOrUpdatePolicy(projectName, templateName, request);
            return ResponseEntity.ok("Policy saved");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving policy");
        }
    }


}