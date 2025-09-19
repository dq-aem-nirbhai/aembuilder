package com.aem.builder.constants;

/**
 * Centralized constants used across AEM Project views and controllers.
 * <p>
 * This class helps avoid hardcoding strings in multiple places
 * by providing a single source of truth for view redirects,
 * file encodings, file extensions, and AEM path prefixes.
 * </p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 *   return AemProjectConstants.REDIRECT_CREATE_COMPONENT;
 * </pre>
 *
 * @author
 * @since 1.0
 */
public final class AemProjectConstants {

    /**
     * Redirect to the project view page.
     */
    public static final String REDIRECT_VIEW = "redirect:/view/";

    /**
     * Redirect to the create component page.
     */
    public static final String REDIRECT_CREATE_COMPONENT = "redirect:/createComponent/";

    /**
     * Redirect pattern for editing a component.
     * <p>
     * Example:
     * <pre>
     *   String redirect = String.format(AemProjectConstants.REDIRECT_EDIT_COMPONENT, projectName, componentName);
     * </pre>
     * </p>
     */
    public static final String REDIRECT_EDIT_COMPONENT = "/editcomponent?componentName=";
    public static final String REDIRECT_INDEX = "redirect:/";
    public static final String PROJECTS_UI_APPS_PATH =
            "/generated-projects/%s/ui.apps/src/main/content/jcr_root";

    /**
     * Default UTF-8 encoding used in file processing.
     */
    public static final String UTF_8 = "UTF-8";
    public static final String USER_DIR_SYS_PROP = "user.dir";


}
