package com.aem.builder.service;

import com.aem.builder.model.DTO.ComponentRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ComponentService {

    List<String> fetchComponentsFromGeneratedProjects(String projectName);

    List<String> getAllComponents() throws IOException;

    void copySelectedComponents(List<String> selectedComponents, String targetPath, String projectName);

    void addComponentsToExistingProject(String projectName, List<String> selectedComponents);

    List<String> getExistingProjects();

    Map<String, List<String>> getProjectComponentsMap(List<String> projects);

    List<String> getCommonComponents(List<String> allComponents, List<String> projectComponents);

    List<String> getDistinctComponents(List<String> allComponents, List<String> projectComponents);

    //component creation
    List<String> getComponentGroups(String projectName);
    void generateComponent(String projectName, ComponentRequest request);

    /**
     * Load an existing component's basic details from the generated project.
     * @param projectName name of the project
     * @param componentName name of the component
     * @return request object populated with component data or null if not found
     */
    ComponentRequest loadComponent(String projectName, String componentName);

    //component checking
    boolean isComponentNameAvailable(String projectName, String componentName);

}