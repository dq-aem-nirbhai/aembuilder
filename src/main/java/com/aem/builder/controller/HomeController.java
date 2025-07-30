package com.aem.builder.controller;

import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AemProjectServiceImpl aemProjectService;

    @GetMapping("/")
    public String test(Model model) throws IOException {
        List<ProjectDetails> projects = aemProjectService.getAllProjects();
        model.addAttribute("projects", projects);
        return "dashboard";
    }

}
