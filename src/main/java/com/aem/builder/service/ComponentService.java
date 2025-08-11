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

    Map<String, String> fetchComponentsWithGroups(String projectName);

    ComponentRequest loadComponent(String projectName, String componentName);

    void updateComponent(String projectName, ComponentRequest request);

    void deleteComponent(String projectName, String componentName);

    String getComponentHtml(String projectName, String componentName);

    String getComponentJava(String projectName, String componentName);

    //component checking
    boolean isComponentNameAvailable(String projectName, String componentName);

}