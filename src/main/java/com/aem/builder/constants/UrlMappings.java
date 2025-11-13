package com.aem.builder.constants;

public final class UrlMappings {
    public static final String FETCH_COMPONENTS = "/fetch-components/{projectname}";
    public static final String ADD_COMPONENTS = "/add-components/{projectname}";
    public static final String CREATE_COMPONENT = "/createComponent/{project}";
    public static final String EDIT_COMPONENT = "/{projectName}/editcomponent";
    public static final String SAVE_COMPONENT = "/saveComponent/{project}";
    public static final String UPDATE_COMPONENT = "/component/update/{project}";
    public static final String DELETE_COMPONENT = "/component/delete/{project}";
    public static final String CHECK_COMPONENT_NAME = "/check-componentName/{projectName}";
    public static final String GROUPED_COMPONENTS = "/grouped-components/{projectName}";
    public static final String POLICIES_COMPONENTS = "/policies-components/{projectName}";
    public static final String CHECK_CHILD_CLASS = "/checkChildJavaClassName";
    public static final String CHECK_TABS = "/checkTabs";
    // Project creation flow
    public static final String CREATE_PROJECT_URL = "/create";
    public static final String CHECK_PROJECT_NAME_URL = "/checkProjectName";
    public static final String SAVE_PROJECT_URL = "/save";
    public static final String DASHBOARD_URL = "/dashboard";
    public static final String VIEW_PROJECT_URL = "/view/{projectName}";
    public static final String DEPLOY_PROJECT_URL= "/{projectName}/deploy";
    public static final String DEPLOY_LOGS_URL = "/{projectName}/deploy/logs";
    public static final String DOWNLOAD_PROJECT_URL = "/download/{projectName}";
    public static final String IMPORT_PROJECT_URL = "/import";
    public static final String CLONE_PROJECT_URL= "/clone";
    public static final String VALIDATE_IMPORT_URL = "/validateImport";
    public static final String DELETE_PROJECT_URL = "/delete/{projectName}";

    // ------------------- GIT BRANCH OPERATIONS -------------------

    public static final String SWITCH_BRANCH_URL= "/{projectName}/switchBranch";
    public static final String RESOLVE_CONFLICTS_URL = "/{projectName}/resolveConflicts";
    public static final String UNSTASH_URL = "/{projectName}/unstash";
    public static final String CREATE_BRANCH_URL= "/{projectName}/createBranch";

    // ------------------- REDIRECT -------------------
    public static final String REDIRECT_VIEW_PROJECT_URL = "redirect:/view/{projectName}";

//----------------------Template------------------
    public static final String SHOW_FOLDER_URL = "/show-folder";
    public static final String FETCH_TEMPLATES = "/fetch-templates/{projectname}";
    public static final String ADD_TEMPLATE = "/add-template/{projectname}";
    public static final String CREATE_TEMPLATE = "/create-template/{projectname}";
    public static final String CREATE_TEMPLATE_GET = "/{projectName}/createtemplate";
    public static final String LIST_TEMPLATES = "/templates/list/{projectname}";
    public static final String TEMPLATE_TYPES = "/template-types/{projectName}";
    public static final String EDIT_TEMPLATE = "/{projectName}/edittemplate";
    public static final String UPDATE_TEMPLATE = "/{projectname}/updatetemplate/{templateName}";
    public static final String DELETE_TEMPLATE="/{projectName}/deletetemplate/{templateName}";
//---------------------policy--------------------------------------------
    public static final String ADD_OR_UPDATE_POLICY = "/policies/add/{projectName}";
    public static final String REDIRECT_POLICY_FORM = "/{projectName}/addpolicy";
    public static final String GET_EXISTING_POLICIES = "/get-existing-policies";
    public static final String GET_POLICY_DETAILS = "/get-policy-details";

    public static final String LOAD_COMPONENT_POLICY = "/api/{project}/component/policy";
    public static final String SHOW_TEMPLATE_COMPONENTS = "/{project}/templates/{template}/components";
    public static final String SHOW_POLICY_EDITOR = "/{project}/templates/{template}/component";
    public static final String SAVE_COMPONENT_POLICY = "/api/{project}/templates/{template}/component/policy";
    public static final String REDIRECT_VIEW_PREFIX = "redirect:/view/";
    public static final String DASHBOARD_REDIRECT = "redirect:/dashboard";


    //---------------  TOOL URLS   -------------


    public static final String FETCH_TOOLS="/fetchtools/{projectname}";
    public static final String ADD_TOOL="/add/{projectname}";
    public static final String EXISTING_TOOLS="/existingtools/{projectname}";
    public static final String TOOL="/tools";


}
