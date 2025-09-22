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
import static com.aem.builder.constants.ModelAttributeKeys.ERROR;
import static com.aem.builder.constants.ModelAttributeKeys.MESSAGE;
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
     * Fetches the list of components for a given AEM project.
     * <p>
     * This method retrieves:
     * <ul>
     *   <li>All available components in the system</li>
     *   <li>Components specific to the provided project</li>
     *   <li>Distinct components that are unique to the system (not in the project)</li>
     *   <li>Common components that exist in both the system and the project</li>
     * </ul>
     *
     * @param projectname the name of the AEM project whose components need to be fetched
     * @return a {@link Map} containing:
     * <ul>
     *     <li><b>UNIQUE_KEY</b>: List of components unique to the system</li>
     *     <li><b>DUPLICATE_KEY</b>: List of components common to both the system and the project</li>
     * </ul>
     * @throws IOException if there is an error while retrieving the components
     */
    @GetMapping(FETCH_COMPONENTS)
    @ResponseBody
    public Map<String, List<String>> getComponents(@PathVariable String projectname) throws IOException {
        log.info("[getComponents] Fetching components for project '{}'", projectname);

        List<String> allComponents = componentService.getAllComponents();
        List<String> projectComponents = componentService.getProjectComponentsMap(projectname);

        List<String> distinctComponents = componentService.getDistinctComponents(allComponents, projectComponents);
        List<String> commonComponents = componentService.getCommonComponents(allComponents, projectComponents);

        log.info("[getComponents] Project '{}'", projectname);
        log.info("[getComponents] Unique components: {}", distinctComponents);
        log.info("[getComponents] Duplicate components: {}", commonComponents);
        log.info("[getComponents] Components fetched successfully for project '{}'", projectname);

        Map<String, List<String>> response = new HashMap<>();
        response.put(UNIQUE_KEY, distinctComponents);
        response.put(DUPLICATE_KEY, commonComponents);

        return response;
    }

    /**
     * Adds the selected components to an existing AEM project.
     * <p>
     * This method receives a list of selected component names and adds them
     * to the specified project. It logs the operation details and returns
     * the appropriate view name based on success or failure.
     * </p>
     *
     * @param projectname        the name of the project to which components will be added
     * @param selectedComponents the list of component names to add to the project
     * @return the name of the view/page to redirect to:
     * <ul>
     *     <li>{@code DASHBOARD_PAGE} – if components are successfully added</li>
     *     <li>{@code CREATE_PPAGE} – if an error occurs while adding components</li>
     * </ul>
     */
    @PostMapping(ADD_COMPONENTS)
    public String addComponentsToExistingProject(
            @PathVariable String projectname,
            @RequestBody List<String> selectedComponents) {

        log.info("[addComponentsToExistingProject] Starting to add components to project '{}'", projectname);
        log.info("[addComponentsToExistingProject] Selected components: {}", selectedComponents);

        try {
            componentService.addComponentsToExistingProject(projectname, selectedComponents);
            log.info("[addComponentsToExistingProject] Components successfully added to project '{}': {}", projectname, selectedComponents);
            return DASHBOARD_PAGE;
        } catch (Exception e) {
            log.error("[addComponentsToExistingProject] Error adding components to project '{}': {}", projectname, e.getMessage(), e);
            return CREATE_PPAGE;
        }
    }

    /**
     * Displays the Create Component form for a specific AEM project.
     * <p>
     * This method prepares the data required to render the component creation page:
     * <ul>
     *     <li>Project name to which the component will belong</li>
     *     <li>Available field types (sorted alphabetically by key)</li>
     *     <li>Existing component groups in the project</li>
     *     <li>Super types of available components within the project</li>
     *     <li>Edit mode flag set to {@code false} (indicates a fresh create operation)</li>
     * </ul>
     *
     * @param project the name of the project for which the component form is being created
     * @param model   the {@link Model} object used to pass attributes to the view
     * @return the name of the Thymeleaf template to render (Create Component page)
     */
    @GetMapping(CREATE_COMPONENT)
    public String showComponentForm(@PathVariable String project, Model model) {
        log.info("[showComponentForm] Preparing to render Create Component form for project '{}'", project);

        model.addAttribute(PROJECT_NAME, project);

        // Retrieve field types and sort alphabetically by key
        var typeResourceMap = FieldType.getTypeResourceMap();
        var sortedByKey = typeResourceMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new // preserve sorted order
                ));

        // Add required attributes to the model
        model.addAttribute(FIELD_TYPES, sortedByKey);
        model.addAttribute(COMPONENT_GROUPS, componentService.getComponentGroups(project));
        model.addAttribute(EDIT_MODE, false);

        Map<String, String> compMap = componentService.fetchComponentSuperTypes(project);
        model.addAttribute(AVAILABLE_COMPONENTS, compMap);

        log.info("[showComponentForm] Field types: {}", sortedByKey.keySet());
        log.info("[showComponentForm] Component groups: {}", componentService.getComponentGroups(project));
        log.info("[showComponentForm] Available super types: {}", compMap.keySet());
        log.info("[showComponentForm] Create Component form rendered successfully for project '{}'", project);

        return CREATE_COMPONENT_PAGE;
    }

    /**
     * Displays the Edit Component form for an existing AEM component within a project.
     * <p>
     * This method loads the selected component's data, prepares the required
     * attributes, and populates the model so that the Thymeleaf template can
     * render the edit form. The form allows updating fields, super types,
     * and source code (HTML/Java).
     * </p>
     *
     * @param componentName the name of the component to edit
     * @param projectName   the name of the project to which the component belongs
     * @param model         the {@link Model} object used to pass attributes to the view
     * @return the name of the Thymeleaf template for the edit component page
     */
    @GetMapping(EDIT_COMPONENT)
    public String showEditComponentForm(@RequestParam String componentName,
                                        @PathVariable String projectName,
                                        Model model) {
        log.info("[showEditComponentForm] Preparing to edit component '{}' in project '{}'",
                componentName, projectName);

        // Load the component details
        ComponentRequest component = componentService.loadComponent(projectName, componentName);
        model.addAttribute(PROJECT_NAME, projectName);

        // Retrieve and sort field types alphabetically
        var typeResourceMap = FieldType.getTypeResourceMap();
        var sortedByKey = typeResourceMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new // preserve sorted order
                ));

        // Add attributes to model for rendering the edit form
        model.addAttribute(FIELD_TYPES, sortedByKey);
        model.addAttribute(COMPONENT_GROUPS, componentService.getComponentGroups(projectName));
        model.addAttribute(EDIT_MODE, true);

        Map<String, String> compMap = componentService.fetchComponentSuperTypes(projectName);
        model.addAttribute(AVAILABLE_COMPONENTS, compMap);
        model.addAttribute(COMPONENT_DATA, component);
        model.addAttribute(HTML_CODE, componentService.getComponentHtml(projectName, componentName));
        model.addAttribute(JAVA_CODE, componentService.getComponentJava(projectName, componentName));

        // Debug logs for key data
        log.info("[showEditComponentForm] Field types loaded: {}", sortedByKey.keySet());
        log.info("[showEditComponentForm] Component groups loaded: {}", componentService.getComponentGroups(projectName));
        log.info("[showEditComponentForm] Available super types: {}", compMap.keySet());
        log.info("[showEditComponentForm] Component '{}' loaded successfully for editing in project '{}'",
                componentName, projectName);

        return CREATE_COMPONENT_PAGE;
    }

    /**
     * Creates and saves a new AEM component for the specified project.
     * <p>
     * This method accepts component details from the form, triggers the service
     * layer to generate the component (including required files and structure),
     * and redirects the user to the appropriate page with a success or error message.
     * </p>
     *
     * @param project            the name of the project in which the component will be created
     * @param request            the {@link ComponentRequest} object containing component details
     * @param redirectAttributes the {@link RedirectAttributes} used to pass flash attributes
     *                           (success or error messages) after redirect
     * @return the redirect path:
     * <ul>
     *     <li>{@code REDIRECT_VIEW + project} – if the component is successfully created</li>
     *     <li>{@code REDIRECT_CREATE_COMPONENT + project} – if an error occurs</li>
     * </ul>
     */
    @PostMapping(SAVE_COMPONENT)
    public String createComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  RedirectAttributes redirectAttributes) {
        log.info("[createComponent] Starting component creation for project '{}'", project);
        log.info("[createComponent] Request details: {}", request);

        try {
            componentService.generateComponent(project, request);
            log.info("[createComponent] Component '{}' created successfully in project '{}'",
                    request.getComponentName(), project);
            redirectAttributes.addFlashAttribute(MESSAGE, request.getComponentName() + " Component created successfully!");
            return REDIRECT_VIEW + project;
        } catch (Exception e) {
            log.error("[createComponent] Error creating component '{}' in project '{}': {}",
                    request.getComponentName(), project, e.getMessage(), e);
            redirectAttributes.addFlashAttribute(ERROR, "Failed to create component: " + e.getMessage());
            return REDIRECT_CREATE_COMPONENT + project;
        }
    }

    /**
     * Updates an existing AEM component in the specified project.
     * <p>
     * This method accepts updated component details from the form, triggers
     * the service layer to update the component’s configuration/files, and
     * redirects the user to the appropriate page with a success or error message.
     * </p>
     *
     * @param project            the name of the project containing the component
     * @param request            the {@link ComponentRequest} object with updated component details
     * @param redirectAttributes the {@link RedirectAttributes} used to pass flash attributes
     *                           (success or error messages) after redirect
     * @return the redirect path:
     * <ul>
     *     <li>{@code REDIRECT_VIEW + project} – if the component is successfully updated</li>
     *     <li>{@code REDIRECT_INDEX + project + REDIRECT_EDIT_COMPONENT + request.getComponentName()}
     *         – if an error occurs while updating</li>
     * </ul>
     */
    @PostMapping(UPDATE_COMPONENT)
    public String updateComponent(@PathVariable String project,
                                  @ModelAttribute ComponentRequest request,
                                  RedirectAttributes redirectAttributes) {
        log.info("[updateComponent] Starting update for component '{}' in project '{}'", request.getComponentName(), project);
        log.info("[updateComponent] Request details: {}", request);

        try {
            componentService.updateComponent(project, request);
            log.info("[updateComponent] Component '{}' updated successfully in project '{}'", request.getComponentName(), project);
            redirectAttributes.addFlashAttribute(MESSAGE, request.getComponentName() + " Component updated successfully!");
            return REDIRECT_VIEW + project;
        } catch (Exception e) {
            log.error("[updateComponent] Error updating component '{}' in project '{}': {}", request.getComponentName(), project, e.getMessage(), e);
            redirectAttributes.addFlashAttribute(ERROR, "Failed to update component: " + e.getMessage());
            return REDIRECT_INDEX + project + REDIRECT_EDIT_COMPONENT + request.getComponentName();
        }
    }

    /**
     * Deletes an existing AEM component from the specified project.
     * <p>
     * This method removes the given component by delegating to the service layer
     * and redirects the user back to the project view page with a success or
     * error message.
     * </p>
     *
     * @param project            the name of the project from which the component will be deleted
     * @param componentName      the name of the component to delete
     * @param redirectAttributes the {@link RedirectAttributes} used to pass flash attributes
     *                           (success or error messages) after redirect
     * @return the redirect path back to the project view page
     */
    @PostMapping(DELETE_COMPONENT)
    public String deleteComponent(@PathVariable String project, @RequestParam String componentName, RedirectAttributes redirectAttributes) {

        log.info("[deleteComponent] Request received to delete component '{}' from project '{}'", componentName, project);
        try {
            componentService.deleteComponent(project, componentName);
            log.info("[deleteComponent] Component '{}' deleted successfully from project '{}'", componentName, project);
            redirectAttributes.addFlashAttribute(MESSAGE, componentName + " Component deleted successfully!");
        } catch (Exception e) {
            log.error("[deleteComponent] Error deleting component '{}' from project '{}': {}", componentName, project, e.getMessage(), e);
            redirectAttributes.addFlashAttribute(ERROR, "Failed to delete component: " + e.getMessage());
        }
        return REDIRECT_VIEW + project;
    }

    /**
     * Checks whether a component name is available within a given AEM project.
     * <p>
     * This method validates if the specified component name already exists in the
     * project. It is typically used for real-time validation (e.g., while creating
     * or editing a component) to prevent duplicate names.
     * </p>
     *
     * @param projectName   the name of the project to check against
     * @param componentName the component name to validate
     * @return {@link ResponseEntity} containing a {@code Boolean}:
     * <ul>
     *     <li>{@code true} – the name is available for use</li>
     *     <li>{@code false} – a component with the same name already exists</li>
     * </ul>
     */
    @GetMapping(CHECK_COMPONENT_NAME)
    public ResponseEntity<Boolean> checkComponentNameExists(@PathVariable String projectName, @RequestParam String componentName) {

        log.info("[checkComponentNameExists] Checking availability of component '{}' in project '{}'", componentName, projectName);
        boolean isAvailable = componentService.isComponentNameAvailable(projectName, componentName);
        log.info("[checkComponentNameExists] Result: component '{}' availability in project '{}': {}", componentName, projectName, isAvailable);
        return ResponseEntity.ok(isAvailable);
    }

    /**
     * Retrieves all components of a given AEM project, grouped by their component groups.
     * <p>
     * This method calls the service layer to fetch components organized into their
     * respective groups (e.g., structure, navigation, content). The result is returned
     * as a JSON response for use in the UI or API consumers.
     * </p>
     *
     * @param projectName the name of the project whose components need to be grouped
     * @return a {@link Map} where:
     * <ul>
     *     <li><b>Key</b> – the name of the component group</li>
     *     <li><b>Value</b> – a list of component names belonging to that group</li>
     * </ul>
     */
    @GetMapping(GROUPED_COMPONENTS)
    @ResponseBody
    public Map<String, List<String>> getComponentsGrouped(@PathVariable String projectName) {

        log.info("[getComponentsGrouped] Request received to fetch grouped components for project '{}'", projectName);
        Map<String, List<String>> groupedComponents = componentService.getComponentsByGroup(projectName);
        log.info("[getComponentsGrouped] Grouped components retrieved for project '{}': {}", projectName, groupedComponents.keySet());
        return groupedComponents;
    }

    /**
     * Retrieves all AEM components of a given project, grouped by their component groups,
     * along with each component's name and full path.
     * <p>
     * This method returns a JSON structure where each component group contains a list of
     * components. Each component entry provides:
     * <ul>
     *     <li><b>name</b> – the component’s name (last folder in the path)</li>
     *     <li><b>path</b> – the full component path in the repository</li>
     * </ul>
     * This is useful for UI rendering (e.g., component policy selection).
     * </p>
     *
     * @param projectName the name of the project whose grouped components should be retrieved
     * @return a {@link Map} where:
     * <ul>
     *     <li><b>Key</b> – component group name</li>
     *     <li><b>Value</b> – list of components (each as a map containing {@code name} and {@code path})</li>
     * </ul>
     * @throws IOException if there is an issue retrieving the components from the repository
     */
    @GetMapping(POLICIES_COMPONENTS)
    @ResponseBody
    public Map<String, List<Map<String, String>>> getGroupedComponents(@PathVariable String projectName) throws IOException {
        log.info("[getGroupedComponents] Fetching grouped components with paths for project '{}'", projectName);

        Map<String, List<String>> groupedComponents = componentService.getComponentsByGroup(projectName);
        Map<String, List<Map<String, String>>> response = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : groupedComponents.entrySet()) {
            String groupName = entry.getKey();
            List<String> components = entry.getValue();
            List<Map<String, String>> componentList = new ArrayList<>();

            for (String comp : components) {
                Map<String, String> compObj = new LinkedHashMap<>();
                compObj.put(KEY_NAME, comp.substring(comp.lastIndexOf("/") + 1)); // component name only
                compObj.put(KEY_PATH, comp); // full component path
                componentList.add(compObj);
            }

            response.put(groupName, componentList);
        }

        log.info("[getGroupedComponents] Retrieved {} component groups for project '{}'",
                response.size(), projectName);

        return response;
    }

    /**
     * Checks whether a child Java class exists for the given project and field name.
     * <p>
     * This endpoint verifies if a model file corresponding to the provided {@code fieldName}
     * is present in the specified {@code projectName}.
     * </p>
     *
     * @param projectName the name of the project where the model class should be checked
     * @param fieldName   the name of the child Java class (model file) to look for
     * @return {@link ResponseEntity} containing:
     * <ul>
     *     <li><b>true</b> – if the child Java class exists</li>
     *     <li><b>false</b> – if it does not exist</li>
     * </ul>
     * @throws IOException if there is an issue accessing the file system or project directory
     */
    @GetMapping(CHECK_CHILD_CLASS)
    public ResponseEntity<Boolean> checkChildJavaClassName(@RequestParam String projectName, @RequestParam String fieldName) throws IOException {

        log.info("[checkChildJavaClassName] Checking if child Java class '{}' exists in project '{}'", fieldName, projectName);
        boolean exists = FileGenerationUtil.checkModelFileExists(projectName, fieldName);
        log.info("[checkChildJavaClassName] Result for project '{}', class '{}': {}", projectName, fieldName, exists ? "FOUND" : "NOT FOUND");

        return ResponseEntity.ok(exists);
    }

    /**
     * Checks whether a given parent component (super type) contains tabs in the specified project.
     * <p>
     * This endpoint accepts a JSON payload with:
     * <ul>
     *   <li><b>projectName</b> – the name of the project</li>
     *   <li><b>superType</b>   – the parent component to inspect</li>
     * </ul>
     * It returns a map describing tab information (if any) for the parent component.
     * </p>
     *
     * @param request a JSON request body containing {@code projectName} and {@code superType}
     * @return a {@link Map} with tab details (empty if no tabs are present)
     * @throws Exception if an error occurs while checking the parent component
     */
    @PostMapping(CHECK_TABS)
    @ResponseBody
    public Map<String, Object> checkIfParentHasTabs(@RequestBody Map<String, String> request) throws Exception {
        String projectName = request.get(PROJECT_NAME);
        String superType = request.get(SUPER_TYPE);

        log.info("[checkIfParentHasTabs] Request received for project='{}', superType='{}'",
                projectName, superType);

        Map<String, Object> parentTabs = componentService.getParentTabs(projectName, superType);

        log.info("[checkIfParentHasTabs] Tabs found for project='{}', superType='{}': {}",
                projectName, superType, parentTabs);

        return parentTabs;
    }


}
