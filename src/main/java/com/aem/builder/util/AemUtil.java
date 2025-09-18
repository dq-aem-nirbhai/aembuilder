package com.aem.builder.util;
import java.io.File;

import static com.aem.builder.constants.AemProjectConstants.COMPONENTS_PATH;
import static com.aem.builder.constants.AemProjectConstants.MSM_FOLDER;

public class AemUtil{


    /**
     * Returns the correct appId folder for a given AEM project.
     *
     * @param  projectsDirPath directory where all projects are stored
     * @param projectName Root project folder (artifactId)
     * @return appId folder name under apps/
     */
    public static String getAppId(String projectsDirPath, String projectName) {

        String appsRootPath = projectsDirPath + "/" + projectName + "/" + COMPONENTS_PATH;
        File appsDir = new File(appsRootPath);

        if (!appsDir.exists() || !appsDir.isDirectory()) {
            throw new IllegalStateException("Apps directory does not exist: " + appsDir.getAbsolutePath());
        }

        // Default fallback: use projectName
        String appName = projectName;

        File[] dirs = appsDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                if (!MSM_FOLDER.equalsIgnoreCase(dir.getName())) { // skip MSM folder
                    appName = dir.getName(); // take the first valid folder
                    break;
                }
            }
        }

        return appName;
    }
}
