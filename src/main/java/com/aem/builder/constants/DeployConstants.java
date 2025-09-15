package com.aem.builder.constants;

/**
 * Constants used in deployment-related services.
 */
public final class DeployConstants {

    private DeployConstants() {} // Prevent instantiation

    /** Log prefix for deployment-related logs */
    public static final String DEPLOY_LOG_PREFIX = "[deployProject]";

    /** Maven log keywords */
    public static final String BUILD_SUCCESS_KEYWORD = "build success";
    public static final String BUILD_FAILED_KEYWORD = "build failed";

    /** Maven log levels */
    public static final String INFO_LOG = "[INFO]";
    public static final String WARN_LOG = "[WARN]";
    public static final String ERROR_LOG = "[ERROR]";
}
