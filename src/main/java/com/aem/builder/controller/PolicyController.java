package com.aem.builder.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PolicyController {

    @GetMapping("/{projectName}/policies")
    public String policyEditor(@PathVariable String projectName, Model model) {
        model.addAttribute("projectName", projectName);
        return "policy-editor";
    }
}
