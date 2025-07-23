package com.aem.builder.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
import com.aem.builder.service.ComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;

    @GetMapping("/components/{projectname}")
    public String getComponentsForProject(@PathVariable String projectname, Model model) throws IOException {
        List<String> existingProjects = componentService.getExistingProjects();
        List<String> allComponents = componentService.getAllComponents();

        // Get already existing components for selected project
        List<String> projectComponents = componentService.getProjectComponentsMap(List.of(projectname))
                .getOrDefault(projectname, new ArrayList<>());

        // Split into distinct and common
        List<String> distinctComponents = componentService.getDistinctComponents(allComponents, projectComponents);
        List<String> commonComponents = componentService.getCommonComponents(allComponents, projectComponents);

        // Set data to model
        model.addAttribute("existingProjects", existingProjects);
        model.addAttribute("componentList", allComponents);
        model.addAttribute("projectComponentsMap", componentService.getProjectComponentsMap(existingProjects));

        model.addAttribute("common", commonComponents);
        model.addAttribute("distinct", distinctComponents);
        model.addAttribute("projectName", projectname);

        return "deploy";
    }

    @PostMapping("/add-components/{projectname}")
    public String addComponentsToExistingProject(
            @PathVariable("projectname") String projectname,
            @RequestParam(value = "selectedComponents", required = false) List<String> selectedComponents,
            Model model) {

        if (selectedComponents != null && !selectedComponents.isEmpty()) {
            componentService.addComponentsToExistingProject(projectname, selectedComponents);
            model.addAttribute("message", "✅ Components added successfully to project: " + projectname);
        } else {
            model.addAttribute("message", "⚠️ No components selected to add.");
        }

    private static final Logger logger = LoggerFactory.getLogger(ComponentController.class);

    @GetMapping("/api/components/{projectName}")
    @ResponseBody
    public Map<String, List<String>> getAvailableComponents(@PathVariable String projectName) {
        logger.info("COMPONENT: Fetching available components for project {}", projectName);

        List<String> uniqueComponents = List.of("HeroBanner", "ImageText", "PromoCard");
        List<String> duplicateComponents = List.of("Carousel", "Testimonial", "Footer", "Header");

        Map<String, List<String>> response = new HashMap<>();
        response.put("unique", uniqueComponents);
        response.put("duplicate", duplicateComponents);

        logger.debug("COMPONENT: Unique = {}, Duplicate = {}", uniqueComponents, duplicateComponents);
        return response;
    }


    @PostMapping("/{projectName}/components/save")
    @ResponseBody
    public Map<String, Object> saveComponents(@PathVariable String projectName,
                                              @RequestBody List<String> selectedComponents) {
        logger.info("Saving components for project: {}", projectName);
        logger.info("Selected components: {}", selectedComponents);
//        Project project = projectService.addComponents(projectName, selectedComponents);
        logger.info("Components saved for project: {}", projectName);
        return Map.of("projectName", "", "templates", new ArrayList<>());
    }

}
        return "dashboard";
    }
}
