package com.aem.builder.controller;

import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.Enum.FieldType;
import com.aem.builder.service.ComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ComponentController {

    private final ComponentService componentService;

    @GetMapping("/fetch-components/{projectname}")
    @ResponseBody
    public Map<String, List<String>> getComponents(@PathVariable String projectname) throws IOException {
        List<String> allComponents = componentService.getAllComponents();
        List<String> projectComponents = componentService
                .getProjectComponentsMap(List.of(projectname))
                .getOrDefault(projectname, new ArrayList<>());

        log.info(projectname);

        List<String> distinctComponents = componentService.getDistinctComponents(allComponents, projectComponents);
        List<String> commonComponents = componentService.getCommonComponents(allComponents, projectComponents);

        log.info("{}", distinctComponents);
        log.info("{}", commonComponents);

        Map<String, List<String>> response = new HashMap<>();
        response.put("unique", distinctComponents);
        response.put("duplicate", commonComponents);

        return response;
    }

    @PostMapping("/add-components/{projectname}")
    public String addComponentsToExistingProject(
            @PathVariable String projectname,
            @RequestBody List<String> selectedComponents) {

        log.info(projectname);
        log.info(selectedComponents.toString());

        try {
            componentService.addComponentsToExistingProject(projectname, selectedComponents);
            return "dashboard";
        } catch (Exception e) {
            return "create";
        }
    }

    //component creation
    @GetMapping("/create/{project}")
    public String showComponentForm(@PathVariable String project, Model model) {
        model.addAttribute("projectName", project);
        model.addAttribute("fieldTypes", FieldType.getTypeResourceMap());
        model.addAttribute("componentGroups", componentService.getComponentGroups(project));
        return "create-component"; // Thymeleaf template
    }

    @PostMapping("/component/create/{project}")
    public String createComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  Model model) {
        componentService.generateComponent(project, request);
        model.addAttribute("message", "Component created successfully!");
        return "redirect:/" + project;
    }

    //component checking
    @GetMapping("/check-componentName/{projectName}")
    public ResponseEntity<Boolean> checkComponentNameExists(
            @PathVariable String projectName,
            @RequestParam String componentName) {

        log.info("{}",componentName);
        log.info("check-component");
        boolean isAvailable = componentService.isComponentNameAvailable(projectName, componentName);
        log.info("{}",isAvailable);
        return ResponseEntity.ok(isAvailable); // true means name is available
    }

    }
