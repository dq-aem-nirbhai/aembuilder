package com.aem.builder.constants;

/**
 * Centralized constants for all controller URL mappings used in the AEM Builder tool. <br>
 *
 * <p>These mappings are stored here to improve readability, prevent hardcoded
 * URLs in controllers, and ensure easier maintainability across the project.</p>
 */
public final class UrlMappings {

    /**
     * Endpoint to fetch all components for a given project.
     */
    public static final String FETCH_COMPONENTS = "/fetch-components/{projectname}";

    /**
     * Endpoint to add selected components to an existing project.
     */
    public static final String ADD_COMPONENTS = "/add-components/{projectname}";

    /**
     * Endpoint to create a new component inside a project.
     */
    public static final String CREATE_COMPONENT = "/createComponent/{project}";

    /**
     * Endpoint to open the edit page for a specific component.
     */
    public static final String EDIT_COMPONENT = "/{projectName}/editcomponent";

    /**
     * Endpoint to save a newly created component.
     */
    public static final String SAVE_COMPONENT = "/saveComponent/{project}";

    /**
     * Endpoint to update an existing component.
     */
    public static final String UPDATE_COMPONENT = "/component/update/{project}";

    /**
     * Endpoint to delete an existing component from a project.
     */
    public static final String DELETE_COMPONENT = "/component/delete/{project}";


    /**
     * Endpoint to check if a component name already exists.
     */
    public static final String CHECK_COMPONENT_NAME = "/check-componentName/{projectName}";

    /**
     * Endpoint to fetch components grouped by category.
     */
    public static final String GROUPED_COMPONENTS = "/grouped-components/{projectName}";

    /**
     * Endpoint to fetch policies-related components for a project.
     */
    public static final String POLICIES_COMPONENTS = "/policies-components/{projectName}";

    /**
     * Endpoint to check whether a child Java class exists for a component.
     */
    public static final String CHECK_CHILD_CLASS = "/checkChildJavaClassName";

    /**
     * Endpoint to validate or fetch available tabs in a dialog.
     */
    public static final String CHECK_TABS = "/checkTabs";
}
