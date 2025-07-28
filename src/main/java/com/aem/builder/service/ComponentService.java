package com.aem.builder.service;

import com.aem.builder.model.DTO.ComponentRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service interface for handling AEM component-related operations
 * such as fetching, copying, comparing, and mapping components
 * between base and generated AEM projects.
 */
public interface ComponentService {

    /**
     * Fetches a list of component names from the generated project structure for a given project name.
     *
     * @param projectName the name of the generated AEM project
     * @return a list of component names from the generated project
     */
    List<String> fetchComponentsFromGeneratedProjects(String projectName);

    /**
     * Retrieves all available component names from the base component repository.
     *
     * @return a list of all base components
     * @throws IOException if file reading fails during component scan
     */
    List<String> getAllComponents() throws IOException;

    /**
     * Copies the selected components from the base repository to the target project path.
     *
     * @param selectedComponents list of selected component names to be copied
     * @param targetPath the absolute path of the target project directory
     * @param projectName the name of the target project
     */
    void copySelectedComponents(List<String> selectedComponents, String targetPath, String projectName);

    /**
     * Adds selected components to an already existing project by copying them from the base.
     *
     * @param projectName the name of the existing project
     * @param selectedComponents list of selected component names to add
     */
    void addComponentsToExistingProject(String projectName, List<String> selectedComponents);

    /**
     * Builds a mapping of project names to their associated component lists.
     *
     * @param projects list of project names
     * @return a map where the key is the project name and the value is the list of component names
     */
    Map<String, List<String>> getProjectComponentsMap(List<String> projects);

    /**
     * Finds common components between the base and project-specific components.
     *
     * @param allComponents list of all base components
     * @param projectComponents list of components present in a specific project
     * @return a list of components that are common to both lists
     */
    List<String> getCommonComponents(List<String> allComponents, List<String> projectComponents);

    /**
     * Finds components that exist in the base but not in the project (i.e., distinct components).
     *
     * @param allComponents list of all base components
     * @param projectComponents list of components present in a specific project
     * @return a list of distinct components not yet present in the project
     */
    List<String> getDistinctComponents(List<String> allComponents, List<String> projectComponents);



    //component creation
    List<String> getComponentGroups(String projectName);
    void generateComponent(String projectName, ComponentRequest request);

    //component checking
    boolean isComponentNameAvailable(String projectName, String componentName);


}
