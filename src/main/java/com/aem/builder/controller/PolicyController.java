package com.aem.builder.controller;

import com.aem.builder.model.PolicyRequest;

import com.aem.builder.service.TemplatePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class PolicyController {

    private final TemplatePolicy policyXmlUpdater;

    public PolicyController(TemplatePolicy policyXmlUpdater) {
        this.policyXmlUpdater = policyXmlUpdater;
    }

    @PostMapping("/policies/add/{projectname}")
    public ResponseEntity<String> addPolicy(@PathVariable("projectname") String projectName,
                                            @RequestBody PolicyRequest request,
                                            @RequestParam(required = false) String templateName) {
        try {
            String policyNodeName=  policyXmlUpdater.addPolicy(projectName,
                    request.getName(),
                    request.getComponentPath(),
                    request.getStyleDefaultClasses(),
                    request.getStyleDefaultElement(),
                    request.getStyles()
            );
            System.out.println(templateName+"      hsgdfsdhghsa    "+"policy node    "+policyNodeName);
            if (templateName != null && !templateName.isEmpty()) {

                policyXmlUpdater.assignPolicyToTemplate(projectName, templateName, policyNodeName);
                System.out.println("hello.....");
            }

            return ResponseEntity.ok("Policy added successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }


    @GetMapping("/{projectName}/addpolicy")
    public String redirectToPolicyForm(@PathVariable("projectName")  String projectName,
                                       @RequestParam String templateName,
                                       Model model) {
        // Pass templateName to form
        model.addAttribute("projectName", projectName);
        model.addAttribute("templateName", templateName);

        // Show the same policy form you already have
        return "policies";  // Thymeleaf page for creating policy
    }
    public String addPolicyToParticularTemplate(){
        return "";
    }




}