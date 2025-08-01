package com.aem.builder.controller;

import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.Enum.FieldType;
import com.aem.builder.service.ComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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


        var typeResourceMap = FieldType.getTypeResourceMap();

        var sortedByKey = typeResourceMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey()) // sort alphabetically by key
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new // keep sorted order
                ));


        model.addAttribute("fieldTypes", sortedByKey);
        model.addAttribute("componentGroups", componentService.getComponentGroups(project));
        // Components that can be extended (core components + existing ones)
        // Use a LinkedHashSet to avoid duplicates while preserving order
        Set<String> available = new LinkedHashSet<>();
        try {
            available.addAll(componentService.fetchComponentsFromGeneratedProjects(project).stream()
                    .map(name -> "/apps/" + project + "/components/" + name)
                    .toList());
            available.addAll(componentService.getAllComponents());
        } catch (IOException e) {
            log.error("Error loading available components", e);
        }
        Map<String, String> compMap = new LinkedHashMap<>();
        for (String path : available) {
            int idx = path.lastIndexOf('/') + 1;
            compMap.put(path, path.substring(idx));
        }
        model.addAttribute("availableComponents", compMap);
        return "create-component"; // Thymeleaf template
    }

    @PostMapping("/component/create/{project}")
    public String createComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            componentService.generateComponent(project, request);
            redirectAttributes.addFlashAttribute("message", "Component created successfully!");
            return "redirect:/" + project;
        } catch (Exception e) {
            log.error("Error creating component", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create component: " + e.getMessage());
            return "redirect:/create/" + project;
        }
    }

    // component edit form
    @GetMapping("/{project}/edit/{component}")
    public String showEditComponent(@PathVariable String project,
                                    @PathVariable String component,
                                    Model model) {
        ComponentRequest details = componentService.getComponentDetails(project, component);
        model.addAttribute("projectName", project);
        model.addAttribute("componentRequest", details);
        model.addAttribute("originalName", component);
        model.addAttribute("fieldTypes", FieldType.getTypeResourceMap());
        model.addAttribute("componentGroups", componentService.getComponentGroups(project));
        return "edit-component";
    }

    @PostMapping("/component/update/{project}")
    public String updateComponent(@PathVariable String project,
                                  @RequestParam String originalName,
                                  @ModelAttribute ComponentRequest request,
                                  Model model) {
        componentService.updateComponent(project, originalName, request);
        model.addAttribute("message", "Component updated successfully!");
        return "redirect:/" + project;
    }

    //component checking

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
