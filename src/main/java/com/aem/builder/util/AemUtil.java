package com.aem.builder.util;

import java.io.File;

import static com.aem.builder.constants.ComponentConstants.COMPONENTS_PATH;
import static com.aem.builder.constants.ComponentConstants.MSM_FOLDER;

public class AemUtil {

    /**
     * Returns the correct appId folder for a given AEM project.
     * Prefers a folder that matches the project name, else falls back
     * to the first valid non-system folder.
     */
    public static String getAppId(String projectsDirPath, String projectName) {

        String appsRootPath = projectsDirPath + "/" + projectName + "/" + COMPONENTS_PATH;
        File appsDir = new File(appsRootPath);

        if (!appsDir.exists() || !appsDir.isDirectory()) {
            throw new IllegalStateException("Apps directory does not exist: " + appsDir.getAbsolutePath());
        }

        // ✅ Step 1: Prefer folder matching the project name (e.g. /apps/mobile)
        File projectDir = new File(appsDir, projectName);
        if (projectDir.exists() && projectDir.isDirectory()) {
            return projectName;
        }

        // ✅ Step 2: Fallback — pick the first valid folder that’s not system-related
        File[] dirs = appsDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String name = dir.getName();
                if (!"cq".equalsIgnoreCase(name)
                        && !MSM_FOLDER.equalsIgnoreCase(name)
                        && !"msm".equalsIgnoreCase(name) &&!"geeksdemo".equalsIgnoreCase(name)) {
                    return name;
                }
            }
        }

        // ✅ Step 3: Final fallback — default to project name
        return projectName;
    }
}
