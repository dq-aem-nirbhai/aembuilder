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
public class ComponentController {

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