package com.aem.builder.controller;

import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.HomeService;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AemProjectServiceImpl aemProjectService;
    private final HomeService homeService;

    @GetMapping("/")
    public String test(Model model) throws IOException {
        List<ProjectDetails> projects = aemProjectService.getAllProjects();
        model.addAttribute("projects", projects);
        return "dashboard";
    }

    @PostMapping("/import")
    public String importProject(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            homeService.importProject(file);
            redirectAttributes.addFlashAttribute("message", "AEM project imported successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to import project");
        }
        return "redirect:/";
    }
}
