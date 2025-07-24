package com.aem.builder.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import java.util.List;

public interface ComponentService {
    List<String> fetchComponentsFromGeneratedProjects(String projectName);

    List<String> getAllComponents() throws IOException;

    void copySelectedComponents(List<String> selectedComponents, String targetPath);

    void addComponentsToExistingProject(String projectName, List<String> selectedComponents);

    List<String> getExistingProjects();

    Map<String, List<String>> getProjectComponentsMap(List<String> projects);

    List<String> getCommonComponents(List<String> allComponents, List<String> projectComponents);

    List<String> getDistinctComponents(List<String> allComponents, List<String> projectComponents);

}
