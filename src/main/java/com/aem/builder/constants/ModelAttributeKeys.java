package com.aem.builder.constants;

/**
 * Constants for Model attributes and project-related paths used across
 * controller views and services in the AEM Builder tool. <br>
 *
 * <p>This centralization ensures consistency, avoids hardcoding, and
 * makes maintainability easier.</p>
 */
public final class ModelAttributeKeys {



    /**
     * Attribute key for the current project name.
     */
    public static final String PROJECT_NAME = "projectName";

    /**
     * Attribute key for available field types in the component dialog.
     */
    public static final String FIELD_TYPES = "fieldTypes";

    /**
     * Attribute key for available component groups.
     */
    public static final String COMPONENT_GROUPS = "componentGroups";

    /**
     * Attribute key indicating whether edit mode is active.
     */
    public static final String EDIT_MODE = "editMode";

    /**
     * Attribute key for the list of available components.
     */
    public static final String AVAILABLE_COMPONENTS = "availableComponents";

    /**
     * Attribute key for component-specific data.
     */
    public static final String COMPONENT_DATA = "componentData";

    /**
     * Attribute key for generated HTML code preview.
     */
    public static final String HTML_CODE = "htmlCode";

    /**
     * Attribute key for generated Java code preview.
     */
    public static final String JAVA_CODE = "javaCode";

    /**
     * Attribute key for projects directory name.
     */
    public static final String PROJECTS_DIR = "generated-projects";


    /**
     * Path to the {@code src/main/resources} folder.
     */
    public static final String SRC_MAIN_RESOURCES = "src/main/resources/";

    /**
     * Path to the AEM components folder inside resources.
     */
    public static final String AEM_COMPONENTS_DIR = "aem-components/";

    /**
     * Path to the {@code src/main/java} folder.
     */
    public static final String SRC_MAIN_JAVA = "src/main/java/";

    /**
     * Directory name where generated projects are stored.
     */
    public static final String GENERATED_PROJECTS_DIR = "generated-projects";


    /**
     * Suffix for model Java files (e.g., {@code MyComponentModel.java}).
     */
    public static final String MODEL_FILE_SUFFIX = "model.java";

    /**
     * Base package prefix for generated Sling Models.
     */
    public static final String MODEL_PACKAGE_PREFIX = "com.aem.builder.slingModels";

    /**
     * Regex for matching package declarations in generated code.
     */
    public static final String PACKAGE_DECLARATION_REGEX =
            "package\\s+com\\.aem\\.builder\\.[\\w.]+;";

    /**
     * Regex for matching Sling Model import statements.
     */
    public static final String IMPORT_STATEMENT_REGEX =
            "import\\s+com\\.aem\\.builder\\.slingModels\\.(\\w+);";


    /**
     * Path to the ui.apps folder under project structure.
     */
    public static final String UI_APPS_PATH = "/ui.apps/src/main/content/jcr_root/apps/";

    /**
     * Prefix for locating resources inside {@code src/main/resources}.
     */
    public static final String CORE_RESOURCE_PATH_PREFIX = "/src/main/resources/";


}
