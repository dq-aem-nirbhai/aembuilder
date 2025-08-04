package com.aem.builder.controller;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.service.AemProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
@Controller
@RequiredArgsConstructor
public class AemProjectController {


    private final AemProjectService aemProjectService;

    @GetMapping("/create")
    public String showCreateForm(Model model) throws IOException {
        model.addAttribute("aemProjectModel", new AemProjectModel());
        model.addAttribute("existingProjects", aemProjectService.getExistingProjects());
        return "create";

    }

    @PostMapping("/save")
    public String saveConfig(@ModelAttribute AemProjectModel aemProjectModel, Model model) {
        aemProjectService.generateAemProject(aemProjectModel);
        model.addAttribute("message", "AEM Project created successfully under generated-projects directory.");
        return "dashboard";
    }
}

