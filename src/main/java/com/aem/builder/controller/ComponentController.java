package com.aem.builder.controller;

import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.Enum.FieldType;
import com.aem.builder.service.ComponentService;
import com.aem.builder.util.FileGenerationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.aem.builder.constants.AemProjectConstants.*;
import static com.aem.builder.constants.ComponentConstants.*;
import static com.aem.builder.constants.ModelAttributeKeys.*;
import static com.aem.builder.constants.UrlMappings.*;
import static com.aem.builder.constants.ViewNames.*;

/**
 * Controller class for handling AEM component CRUD operations.
 * This controller manages the creation, editing, updating, deletion,
 * and retrieval of components within a project.
 * <p>
 * All URL mappings, model attributes, and view names are centralized
 * in constants for better maintainability.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ComponentController {

    private final ComponentService componentService;


    /**
     * Fetches unique and duplicate components for a given project.
     *
     * @param projectname Name of the project
     * @return Map containing unique and duplicate components
     * @throws IOException if an error occurs while accessing files
     */
    /*@GetMapping(UrlMappings.FETCH_COMPONENTS)
    @ResponseBody
    public Map<String, List<String>> getComponents(@PathVariable String projectname) throws IOException {
        List<String> allComponents = componentService.getAllComponents();
        List<String> projectComponents = componentService.getProjectComponentsMap(projectname);

        List<String> distinctComponents = componentService.getDistinctComponents(allComponents, projectComponents);
        List<String> commonComponents = componentService.getCommonComponents(allComponents, projectComponents);

        log.info("[getComponents] Project '{}'", projectname);
        log.info("[getComponents] Unique components: {}", distinctComponents);
        log.info("[getComponents] Duplicate components: {}", commonComponents);

        Map<String, List<String>> response = new HashMap<>();
        response.put(UNIQUE_KEY, distinctComponents);
        response.put(DUPLICATE_KEY, commonComponents);

        return response;
    }*/
    @GetMapping(FETCH_COMPONENTS)
    @ResponseBody
    public Map<String, List<String>> getComponents(@PathVariable String projectname) throws IOException {
        List<String> allComponents = componentService.getAllComponents();
        List<String> projectComponents = componentService
                .getProjectComponentsMap(projectname);

        List<String> distinctComponents = componentService.getDistinctComponents(allComponents, projectComponents);
        List<String> commonComponents = componentService.getCommonComponents(allComponents, projectComponents);

        log.info("[getComponents] Project '{}'", projectname);
        log.info("[getComponents] Unique components: {}", distinctComponents);
        log.info("[getComponents] Duplicate components: {}", commonComponents);

        Map<String, List<String>> response = new HashMap<>();
        response.put(UNIQUE_KEY, distinctComponents);
        response.put(DUPLICATE_KEY, commonComponents);

        return response;
    }

    /**
     * Adds selected components to an existing project.
     *
     * @param projectname        Name of the project
     * @param selectedComponents List of components to add
     * @return Redirect view name
     */
    /*@PostMapping(UrlMappings.ADD_COMPONENTS)
    public String addComponentsToExistingProject(@PathVariable String projectname,
                                                 @RequestBody List<String> selectedComponents) {
        try {
            componentService.addComponentsToExistingProject(projectname, selectedComponents);
            log.info("[addComponentsToExistingProject] Components added to project '{}': {}", projectname, selectedComponents);
            return ViewNames.DASHBOARD_PAGE;
        } catch (Exception e) {
            log.error("[addComponentsToExistingProject] Error adding components to project '{}': {}", projectname, e.getMessage());
            return ViewNames.CREATE_PPAGE;
        }
    }*/
    @PostMapping(ADD_COMPONENTS)
    public String addComponentsToExistingProject(
            @PathVariable String projectname,
            @RequestBody List<String> selectedComponents) {
        log.info("[addComponentsToExistingProject]" + projectname);
        log.info("[addComponentsToExistingProject]" + selectedComponents.toString());
        try {
            componentService.addComponentsToExistingProject(projectname, selectedComponents);
            log.info("[addComponentsToExistingProject] Components added to project '{}': {}", projectname, selectedComponents);
            return DASHBOARD_PAGE;
        } catch (Exception e) {
            log.error("[addComponentsToExistingProject] Error adding components to project '{}': {}", projectname, e.getMessage());
            return CREATE_PPAGE;
        }
    }


    /**
     * Shows the form for creating a new component.
     *
     * @param project Project name
     * @param model   Spring model to pass attributes to the view
     * @return Name of Thymeleaf template
     */
    /*@GetMapping(UrlMappings.CREATE_COMPONENT)
    public String showComponentForm(@PathVariable String project, Model model) {
        // Add model attributes
        model.addAttribute(PROJECT_NAME, project);
        model.addAttribute(FIELD_TYPES, getSortedFieldTypes());
        model.addAttribute(COMPONENT_GROUPS, componentService.getComponentGroups(project));
        model.addAttribute(ModelAttributeKeys.EDIT_MODE, false);
        model.addAttribute(ModelAttributeKeys.AVAILABLE_COMPONENTS, componentService.fetchComponentSuperTypes(project));

        // Log at method level
        log.info("[showComponentForm] Rendering create component form for project '{}'", project);
        return CREATE_COMPONENT_PAGE;
    }*/
    //component creation
    @GetMapping(CREATE_COMPONENT)
    public String showComponentForm(@PathVariable String project, Model model) {
        model.addAttribute(PROJECT_NAME, project);
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

        model.addAttribute(FIELD_TYPES, sortedByKey);
        model.addAttribute(COMPONENT_GROUPS, componentService.getComponentGroups(project));
        model.addAttribute(EDIT_MODE, false);
        Map<String, String> compMap = componentService.fetchComponentSuperTypes(project);


        model.addAttribute(AVAILABLE_COMPONENTS, compMap);
        log.info("[showComponentForm] Rendering create component form for project '{}'", project);
        return CREATE_COMPONENT_PAGE; // Thymeleaf template
    }

    /**
     * Shows the form for editing an existing component.
     *
     * @param componentName Component name to edit
     * @param projectName   Project name
     * @param model         Spring model to pass attributes to the view
     * @return Name of Thymeleaf template
     */
  /*  @GetMapping(UrlMappings.EDIT_COMPONENT)
    public String showEditComponentForm(@RequestParam String componentName,
                                        @PathVariable String projectName,
                                        Model model) {
        ComponentRequest component = componentService.loadComponent(projectName, componentName);

        // Add model attributes
        model.addAttribute(PROJECT_NAME, projectName);
        model.addAttribute(ModelAttributeKeys.FIELD_TYPES, getSortedFieldTypes());
        model.addAttribute(ModelAttributeKeys.COMPONENT_GROUPS, componentService.getComponentGroups(projectName));
        model.addAttribute(ModelAttributeKeys.EDIT_MODE, true);
        model.addAttribute(ModelAttributeKeys.AVAILABLE_COMPONENTS, componentService.fetchComponentSuperTypes(projectName));
        model.addAttribute(ModelAttributeKeys.COMPONENT_DATA, component);
        model.addAttribute(ModelAttributeKeys.HTML_CODE, componentService.getComponentHtml(projectName, componentName));
        model.addAttribute(ModelAttributeKeys.JAVA_CODE, componentService.getComponentJava(projectName, componentName));

        // Log at method level
        log.info("[showEditComponentForm] Editing component '{}' in project '{}'", componentName, projectName);
        return ViewNames.CREATE_COMPONENT_PAGE;
    }*/
    @GetMapping(EDIT_COMPONENT)
    public String showEditComponentForm(@RequestParam String componentName,
                                        @PathVariable String projectName,
                                        Model model) {
        ComponentRequest component = componentService.loadComponent(projectName, componentName);
        model.addAttribute(PROJECT_NAME, projectName);
        var typeResourceMap = FieldType.getTypeResourceMap();
        var sortedByKey = typeResourceMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));
        model.addAttribute(FIELD_TYPES, sortedByKey);
        model.addAttribute(COMPONENT_GROUPS, componentService.getComponentGroups(projectName));
        model.addAttribute(EDIT_MODE, true);
        Map<String, String> compMap = componentService.fetchComponentSuperTypes(projectName);
        log.info("superTypessss...{}", compMap);

        model.addAttribute(AVAILABLE_COMPONENTS, compMap);
        model.addAttribute(COMPONENT_DATA, component);
        model.addAttribute(HTML_CODE, componentService.getComponentHtml(projectName, componentName));
        model.addAttribute(JAVA_CODE, componentService.getComponentJava(projectName, componentName));
        log.info("[showEditComponentForm] Editing component '{}' in project '{}'", componentName, projectName);
        return CREATE_COMPONENT_PAGE;
    }

    /**
     * Saves a newly created component.
     *
     * @param project            Project name
     * @param request            ComponentRequest containing component details
     * @param redirectAttributes Redirect attributes for flash messages
     * @return Redirect URL
     */

    @PostMapping(SAVE_COMPONENT)
    public String createComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  RedirectAttributes redirectAttributes) {
        log.info("Request Details,{}", request);
        try {
            componentService.generateComponent(project, request);
            redirectAttributes.addFlashAttribute(MESSAGE, request.getComponentName() + " Component created successfully!");
            return REDIRECT_VIEW + project;
        } catch (Exception e) {
            log.error("Error creating component", e);
            redirectAttributes.addFlashAttribute(ERROR, "Failed to create component: " + e.getMessage());
            return REDIRECT_CREATE_COMPONENT + project;
        }
    }

    /**
     * Updates an existing component.
     *
     * @param project            Project name
     * @param request            ComponentRequest containing updated component details
     * @param redirectAttributes Redirect attributes for flash messages
     * @return Redirect URL
     */
  /*  @PostMapping(UrlMappings.UPDATE_COMPONENT)
    public String updateComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  RedirectAttributes redirectAttributes) {

        log.info("Request........{}", request);
        try {
            componentService.updateComponent(project, request);
            redirectAttributes.addFlashAttribute(MESSAGE,
                    request.getComponentName() + " Component updated successfully!");
            log.info("[updateComponent] Component '{}' updated successfully in project '{}'", request.getComponentName(), project);
            return REDIRECT_VIEW + project;
        } catch (Exception e) {
            log.error("[updateComponent] Error updating component '{}' in project '{}': {}", request.getComponentName(), project, e.getMessage());
            redirectAttributes.addFlashAttribute(ERROR,
                    "Failed to update component: " + e.getMessage());
            return String.format(AemProjectConstants.REDIRECT_EDIT_COMPONENT, project, request.getComponentName());
        }
    }*/
    @PostMapping(UPDATE_COMPONENT)
    public String updateComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            componentService.updateComponent(project, request);
            redirectAttributes.addFlashAttribute(MESSAGE, request.getComponentName() + " Component updated successfully!");
            return REDIRECT_VIEW + project;
        } catch (Exception e) {
            log.error("Error updating component", e);
            redirectAttributes.addFlashAttribute(ERROR, "Failed to update component: " + e.getMessage());
            return REDIRECT_INDEX + project + REDIRECT_EDIT_COMPONENT + request.getComponentName();
        }
    }

    /**
     * Deletes a component from the project.
     *
     * @param project            Project name
     * @param componentName      Component name to delete
     * @param redirectAttributes Redirect attributes for flash messages
     * @return Redirect URL
     */


    @PostMapping(DELETE_COMPONENT)
    public String deleteComponent(@PathVariable String project,
                                  @RequestParam String componentName,
                                  RedirectAttributes redirectAttributes) {
        try {
            componentService.deleteComponent(project, componentName);
            redirectAttributes.addFlashAttribute(MESSAGE, componentName + " Component deleted successfully!");
            log.info("[deleteComponent] Component '{}' deleted from project '{}'", componentName, project);
        } catch (Exception e) {
            log.error("[deleteComponent] Error deleting component '{}' from project '{}': {}", componentName, project, e.getMessage());
            redirectAttributes.addFlashAttribute(ERROR, "Failed to delete component: " + e.getMessage());
        }
        return REDIRECT_VIEW + project;
    }

    /**
     * Checks if a component name already exists in the project.
     *
     * @param projectName   Project name
     * @param componentName Component name to check
     * @return true if available, false if already exists
     */

    //component checking
    @GetMapping(CHECK_COMPONENT_NAME)
    public ResponseEntity<Boolean> checkComponentNameExists(
            @PathVariable String projectName,
            @RequestParam String componentName) {

        log.info("{}", componentName);
        log.info("check-component");
        boolean isAvailable = componentService.isComponentNameAvailable(projectName, componentName);
        log.info("[checkComponentNameExists] Availability of component '{}' in project '{}': {}", componentName, projectName, isAvailable);
        return ResponseEntity.ok(isAvailable); // true means name is available
    }

    /**
     * Returns components grouped by their group names.
     *
     * @param projectName Project name
     * @return Map of grouped components
     */
    @GetMapping(GROUPED_COMPONENTS)
    @ResponseBody
    public Map<String, List<String>> getComponentsGrouped(@PathVariable String projectName) {
        System.out.println(componentService.getComponentsByGroup(projectName));
        log.info("[getComponentsGrouped] Fetching grouped components for project '{}'", projectName);
        return componentService.getComponentsByGroup(projectName);
    }

    @GetMapping(POLICIES_COMPONENTS)
    @ResponseBody
    public Map<String, List<Map<String, String>>> getGroupedComponents(@PathVariable String projectName) throws IOException {
        log.info("{} Fetching grouped components with paths for project '{}'", LOG_PREFIX, projectName);
        Map<String, List<String>> groupedComponents = componentService.getComponentsByGroup(projectName);
        Map<String, List<Map<String, String>>> response = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : groupedComponents.entrySet()) {
            String groupName = entry.getKey();
            List<String> components = entry.getValue();

            List<Map<String, String>> componentList = new ArrayList<>();

            for (String comp : components) {
                Map<String, String> compObj = new LinkedHashMap<>();
                compObj.put(KEY_NAME, comp.substring(comp.lastIndexOf("/") + 1)); // just the last folder
                compObj.put(KEY_PATH, comp); // full path
                componentList.add(compObj);
            }
            response.put(groupName, componentList);
        }


        return response;
    }


    /**
     * Checks if a child Java model class for a multifield exists in the project.
     *
     * @param projectName Project name
     * @param fieldName   Field name representing the multifield
     * @return ResponseEntity containing true if class exists, false otherwise
     * @throws IOException if file check fails
     */
    @GetMapping(CHECK_CHILD_CLASS)
    public ResponseEntity<Boolean> checkChildJavaClassName(@RequestParam String projectName,
                                                           @RequestParam String fieldName) throws IOException {

        boolean exists = FileGenerationUtil.checkModelFileExists(projectName, fieldName);
        log.info("{} Checking if child Java class '{}' exists in project '{}': {}",
                LOG_PREFIX, fieldName, projectName, exists);
        return ResponseEntity.ok(exists); // returns true or false
    }

    /**
     * Checks if a parent component has tabs based on its superType.
     *
     * @param request JSON payload containing projectName and superType
     * @return Map containing tab details for the parent component
     * @throws Exception if service fails
     */
    @PostMapping(CHECK_TABS)
    public Map<String, Object> checkIfParentHasTabs(@RequestBody Map<String, String> request) throws Exception {
        String projectName = request.get(PROJECT_NAME);
        String superType = request.get(SUPER_TYPE);


        log.info("Parent Tabs........., {}", componentService.getParentTabs(projectName, superType));
        return componentService.getParentTabs(projectName, superType);
    }


}
