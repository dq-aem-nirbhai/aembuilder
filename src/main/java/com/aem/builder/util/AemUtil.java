package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

import static com.aem.builder.constants.ComponentConstants.COMPONENTS_PATH;
import static com.aem.builder.constants.ComponentConstants.MSM_FOLDER;
@Slf4j
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
            // Instead of throwing an exception, just log and continue
            log.warn("⚠️ Apps directory does not exist: {}. Returning default appId '{}'.", appsDir.getAbsolutePath(), projectName);
            return projectName; // fallback
        }

        File projectDir = new File(appsDir, projectName);
        if (projectDir.exists() && projectDir.isDirectory()) {
            return projectName;
        }

        File[] dirs = appsDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String name = dir.getName();
                if (!"cq".equalsIgnoreCase(name)
                        && !MSM_FOLDER.equalsIgnoreCase(name)
                        && !"msm".equalsIgnoreCase(name)
                        && !"geeksdemo".equalsIgnoreCase(name)) {
                    return name;
                }
            }
        }

        return projectName;
    }

}
