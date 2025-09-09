package com.aem.builder.controller;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.Enum.FieldType;
import com.aem.builder.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;



@Controller
@RequiredArgsConstructor
@Slf4j
public class ComponentController {

    private final ComponentService componentService;

    private final UpdateHTL updatehtl;
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
        model.addAttribute("editMode", false);
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

    @GetMapping("/{projectName}/editcomponent")
    public String showEditComponentForm(@RequestParam String componentName,
                                        @PathVariable String projectName,
                                        Model model) {
        ComponentRequest component = componentService.loadComponent(projectName, componentName);

        model.addAttribute("projectName", projectName);

        var typeResourceMap = FieldType.getTypeResourceMap();

        var sortedByKey = typeResourceMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));

        model.addAttribute("fieldTypes", sortedByKey);
        model.addAttribute("componentGroups", componentService.getComponentGroups(projectName));
        model.addAttribute("editMode", true);

        // available components
        Set<String> available = new LinkedHashSet<>();
        try {
            available.addAll(componentService.fetchComponentsFromGeneratedProjects(projectName).stream()
                    .map(name -> "/apps/" + projectName + "/components/" + name)
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
        model.addAttribute("componentData", component);
        model.addAttribute("htmlCode", componentService.getComponentHtml(projectName, componentName));
        model.addAttribute("javaCode", componentService.getComponentJava(projectName, componentName));
        return "create-component";
    }

    @PostMapping("/component/create/{project}")
    public String createComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            componentService.generateComponent(project, request);
            redirectAttributes.addFlashAttribute("message", "Component created successfully!");
            return "redirect:/view/" + project;
        } catch (Exception e) {
            log.error("Error creating component", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create component: " + e.getMessage());
            return "redirect:/create/" + project;
        }
    }

@Autowired
    UpdateComponent updateComponent;
    @PostMapping("/component/update/{projectName}")
    public String updateComponent(
            @PathVariable String projectName,
            @ModelAttribute ComponentRequest componentRequest,
            RedirectAttributes redirectAttributes) throws Exception {

        System.out.println("New request: " + componentRequest);

        // Load old component state
        ComponentRequest oldRequest = componentService.loadComponent(projectName, componentRequest.getComponentName());
        System.out.println("Old request: " + oldRequest);

        // Locate dialog.xml
        String dialogPath = "generated-projects/" + projectName
                + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components/"
                + componentRequest.getComponentName()
                + "/_cq_dialog/.content.xml";

        File dialogFile = new File(dialogPath);
        if (!dialogFile.exists()) {
            throw new IllegalStateException("Dialog file not found at " + dialogPath);
        }

        // Call service method to update dialog only
        updateComponent.updateDialog(dialogFile, componentRequest.getFields());



        //sling model update
        updateComponent.updateSlingModel(componentRequest);

//htl update
        List<ComponentField> fields = componentRequest.getFields();
        log.info("fields from the new request, {}",fields);
        String htlFile = "generated-projects/"+ projectName+
                "/ui.apps/src/main/content/jcr_root/apps/"+
                projectName+ "/components/"+ componentRequest.getComponentName()+
                "/"+componentRequest.getComponentName() + ".html";
          log.info("htl path to update {}",htlFile);

        updatehtl.updateHTLFromRequest(componentRequest,htlFile);
        redirectAttributes.addFlashAttribute("message", "Dialog updated successfully!");
        return "redirect:/view/" + projectName;
    }




    @GetMapping("/component/edit/{projectName}")
    public String editComponentPage(@PathVariable String projectName,
                                    @RequestParam String componentName,
                                    Model model) {
        return showEditComponentForm(componentName, projectName, model);
    }


    @PostMapping("/component/delete/{project}")
    public String deleteComponent(@PathVariable String project,
                                  @RequestParam String componentName,
                                  RedirectAttributes redirectAttributes) {
        try {
            componentService.deleteComponent(project, componentName);
            redirectAttributes.addFlashAttribute("message", "Component deleted successfully!");
        } catch (Exception e) {
            log.error("Error deleting component", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete component: " + e.getMessage());
        }
        return "redirect:/view/" + project;
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



    @GetMapping("/grouped-components/{projectName}")
    @ResponseBody
    public Map<String, List<String>> getComponentsGrouped(@PathVariable String projectName) {
        System.out.println(componentService.getComponentsByGroup(projectName));

        return componentService.getComponentsByGroup(projectName);
    }
    @GetMapping("/policies-components/{projectName}")
    @ResponseBody
    public Map<String, List<Map<String, String>>> getGroupedComponents(@PathVariable String projectName) throws IOException {
        // Fetch grouped components from service
        Map<String, List<String>> groupedComponents = componentService.getComponentsByGroup(projectName);

        Map<String, List<Map<String, String>>> response = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : groupedComponents.entrySet()) {
            String groupName = entry.getKey();
            List<String> components = entry.getValue();

            List<Map<String, String>> componentList = new ArrayList<>();

            for (String comp : components) {
                Map<String, String> compObj = new LinkedHashMap<>();

                // comp is already a full path like /apps/accenture/components/form/options
                compObj.put("name", comp.substring(comp.lastIndexOf("/") + 1)); // just the last folder
                compObj.put("path", comp); // full path
                componentList.add(compObj);
            }
            response.put(groupName, componentList);
        }

        return response;
    }


}

                                                                                                                                                                                                                                                                                                                                                    