package com.aem.builder.constants;

public final class UrlMappings {

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

    public static final String SHOW_FOLDER_URL = "/show-folder";
}
