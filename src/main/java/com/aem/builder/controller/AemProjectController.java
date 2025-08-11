package com.aem.builder.controller;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.service.ComponentService;
import com.aem.builder.service.TemplateService;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AemProjectController {

    private final AemProjectServiceImpl aemProjectService;
    private final TemplateService templateService;
    private final ComponentService componentService;

    @GetMapping("/create")
    public String test(Model model) throws IOException {
        List<String> templates = templateService.getTemplateFileNames();
        model.addAttribute("templates", templates);
        model.addAttribute("aemProjectModel", new AemProjectModel());

        List<String> components = new ArrayList<>(componentService.getAllComponents());
        Collections.sort(components);
        model.addAttribute("componentList", components);
        return "create";
    }

    @PostMapping("/save")
    public String saveConfig(@ModelAttribute AemProjectModel aemProjectModel, Model model) {
        try {
            aemProjectService.generateAemProject(aemProjectModel);
            model.addAttribute("message", "AEM Project created successfully under generated-projects directory.");
            return "redirect:/";
        } catch (IOException e) {
            model.addAttribute("error", e.getMessage());
            try {
                List<String> templates = templateService.getTemplateFileNames();
                model.addAttribute("templates", templates);
                List<String> components = new ArrayList<>(componentService.getAllComponents());
                Collections.sort(components);
                model.addAttribute("componentList", components);
            } catch (IOException ignored) {
            }
            return "create";
        }
    }
}
