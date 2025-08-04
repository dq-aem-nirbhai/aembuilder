package com.aem.builder.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    @GetMapping("/deploy")
    public String deploy() {
        return "deploy";
    }

    @GetMapping("/template/{templateId}/components")
    public String components(@PathVariable String templateId, Model model) {
        model.addAttribute("templateId", templateId);
        return "components";
    }

    @GetMapping("/template/{templateId}/component/{componentId}/policy")
    public String policy(@PathVariable String templateId, @PathVariable String componentId, Model model) {
        model.addAttribute("templateId", templateId);
        model.addAttribute("componentId", componentId);
        return "policy";
    }
}
