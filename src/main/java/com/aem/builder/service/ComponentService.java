package com.aem.builder.service;

import com.aem.builder.model.DTO.ComponentRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service interface for managing AEM components within generated projects.
 * Provides operations for creating, updating, deleting, and retrieving component metadata.
 */
public interface ComponentService {

    /**
     * Fetch all components from a given generated project.
     *
     * @param projectName the project name
     * @return list of component names
     */
    List<String> fetchComponentsFromGeneratedProjects(String projectName);

    /**
     * Get all components across available projects.
     *
     * @return list of all components
     * @throws IOException if an error occurs while fetching components
     */
    List<String> getAllComponents() throws IOException;

    /**
     * Copy selected components into the given project.
     *
     * @param selectedComponents list of component names to copy
     * @param targetPath         the target path for copied components
     * @param projectName        the project name
     */
    void copySelectedComponents(List<String> selectedComponents, String targetPath, String projectName);

    /**
     * Add components to an existing project.
     *
     * @param projectName        the project name
     * @param selectedComponents components to be added
     */
    void addComponentsToExistingProject(String projectName, List<String> selectedComponents);

    /**
     * Retrieve all existing project names.
     *
     * @return list of project names
     */
    List<String> getExistingProjects();

    /**
     * Retrieve all components for a specific project.
     *
     * @param projectName the project name
     * @return list of components
     */
    List<String> getProjectComponentsMap(String projectName);

    /**
     * Get common components between all components and project components.
     *
     * @param allComponents     list of all available components
     * @param projectComponents list of components in the project
     * @return list of common components
     */
    List<String> getCommonComponents(List<String> allComponents, List<String> projectComponents);

    /**
     * Get distinct components between all components and project components.
     *
     * @param allComponents     list of all available components
     * @param projectComponents list of components in the project
     * @return list of distinct components
     */
    List<String> getDistinctComponents(List<String> allComponents, List<String> projectComponents);

    /**
     * Get available component groups in the project.
     *
     * @param projectName the project name
     * @return list of component groups
     */
    List<String> getComponentGroups(String projectName);

    /**
     * Generate a new component inside the given project.
     *
     * @param projectName the project name
     * @param request     component request details
     */
    void generateComponent(String projectName, ComponentRequest request);

    /**
     * Fetch components with their associated groups.
     *
     * @param projectName the project name
     * @return map of component name to group
     */
    Map<String, String> fetchComponentsWithGroups(String projectName);

    /**
     * Load an existing component for editing.
     *
     * @param projectName   the project name
     * @param componentName the component name
     * @return component request containing details
     */
    ComponentRequest loadComponent(String projectName, String componentName);

    /**
     * Update an existing component in the project.
     *
     * @param projectName the project name
     * @param request     updated component request
     */
    void updateComponent(String projectName, ComponentRequest request);

    /**
     * Delete a component from a project.
     *
     * @param projectName   the project name
     * @param componentName the component name
     */
    void deleteComponent(String projectName, String componentName);

    /**
     * Get the HTML source of a component.
     *
     * @param projectName   the project name
     * @param componentName the component name
     * @return HTML as string
     */
    String getComponentHtml(String projectName, String componentName);

    /**
     * Get the Java source of a component.
     *
     * @param projectName   the project name
     * @param componentName the component name
     * @return Java source as string
     */
    String getComponentJava(String projectName, String componentName);

    /**
     * Check if a component name is available in the project.
     *
     * @param projectName   the project name
     * @param componentName the component name
     * @return true if available, false otherwise
     */
    boolean isComponentNameAvailable(String projectName, String componentName);

    /**
     * Get components organized by group.
     *
     * @param projectName the project name
     * @return map of group name to component list
     */
    Map<String, List<String>> getComponentsByGroup(String projectName);

    /**
     * Fetch component super types for a project.
     *
     * @param projectName the project name
     * @return map of component name to its super type
     */
    Map<String, String> fetchComponentSuperTypes(String projectName);

    /**
     * Retrieve parent tabs for a given super type in a project.
     *
     * @param projectName the project name
     * @param superType   the super type
     * @return map containing parent tab details
     */
    Map<String, Object> getParentTabs(String projectName, String superType);
}
