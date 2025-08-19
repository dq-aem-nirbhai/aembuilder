package com.aem.builder.controller;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.service.ComponentService;
import com.aem.builder.service.TemplateService;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AemProjectController {

    private final AemProjectServiceImpl aemProjectService;
    private final TemplateService templateService;
    private final ComponentService componentService;

    @GetMapping("/create")
    public String test(Model model) throws IOException {
        List<String> templates = templateService.getTemplateFileNames();
        model.addAttribute("templates", templates);
        model.addAttribute("aemProjectModel", new AemProjectModel());
        model.addAttribute("componentList", componentService.getAllComponents());
        return "create";
    }

    @GetMapping("/checkProjectName")
    @ResponseBody
    public Map<String, Object> checkProjectName(@RequestParam String name) {
        boolean exists = aemProjectService.projectExists(name);
log.info("exits:{}:",exists);
        Map<String, Object> response = new HashMap<>();
        response.put("exists", exists);
        response.put("message", exists ? "❌ Project already exists" : "✅ Project name is available");
        log.info("{}",response);
        return response;
    }

    @PostMapping("/save")

    public String saveConfig(@ModelAttribute AemProjectModel aemProjectModel, Model model) {
        try {
            aemProjectService.generateAemProject(aemProjectModel);
            model.addAttribute("message", "AEM Project created successfully under generated-projects directory.");
            return "redirect:/dashboard";
        } catch (IOException e) {
            model.addAttribute("error", e.getMessage());
            try {
                List<String> templates = templateService.getTemplateFileNames();
                model.addAttribute("templates", templates);
                model.addAttribute("componentList", componentService.getAllComponents());
            } catch (IOException ignored) {
            }
            return "create";
        }
    }}
