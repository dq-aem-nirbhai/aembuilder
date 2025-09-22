package com.aem.builder.constants;

/**
 * Constants for Model attributes and project-related paths used across
 * controller views and services in the AEM Builder tool. <br>
 *
 * <p>This centralization ensures consistency, avoids hardcoding, and
 * makes maintainability easier.</p>
 */
public final class ModelAttributeKeys {

    public static final String PROJECT_NAME = "projectName";
    public static final String FIELD_TYPES = "fieldTypes";
    public static final String COMPONENT_GROUPS = "componentGroups";
    public static final String EDIT_MODE = "editMode";
    public static final String AVAILABLE_COMPONENTS = "availableComponents";
    public static final String COMPONENT_DATA = "componentData";
    public static final String HTML_CODE = "htmlCode";
    public static final String JAVA_CODE = "javaCode";
    public static final String PROJECTS_DIR = "generated-projects";
    public static final String GENERATED_PROJECTS_DIR = "generated-projects";
    public static final String MODEL_FILE_SUFFIX = "model.java";
    public static final String PACKAGE_DECLARATION_REGEX =
            "package\\s+com\\.aem\\.builder\\.[\\w.]+;";
    public static final String IMPORT_STATEMENT_REGEX =
            "import\\s+com\\.aem\\.builder\\.slingModels\\.(\\w+);";
    public static final String CORE_RESOURCE_PATH_PREFIX = "/src/main/resources/";
    public static final String MESSAGE = "message";
    public static final String ERROR = "error";
    public static final String TEMPLATES = "templates";
    public static final String COMPONENT_LIST = "componentList";
    public static final String AEM_PROJECT_MODEL = "aemProjectModel";
    public static final String BRANCH = "branch";
    public static final String BRANCHES = "branches";
    public static final String HAS_STASH = "hasStash";
    public static final String COMPONENTS = "components";
    public static final String EDITABLE_COMPONENTS = "editableComponents";
    public static final String CAN_DEPLOY = "canDeploy";
    public static final String PROJECTS = "projects";
    public static final String CONFLICTS = "conflicts";
    public static final String TARGET_BRANCH = "targetBranch";
    public static final String BRANCH_ACTION = "branchAction";
    public static final String FROM_BRANCH = "fromBranch";

}
