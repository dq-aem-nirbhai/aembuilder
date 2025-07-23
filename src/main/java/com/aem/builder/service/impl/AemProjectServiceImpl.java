package com.aem.builder.service.impl;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.service.AemProjectService;
import com.aem.builder.service.ComponentService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
@AllArgsConstructor
public class AemProjectServiceImpl implements AemProjectService {

    private final ComponentService componentService;

    @Override
    public void generateAemProject(AemProjectModel aemProjectModel) {
        try {
            // Create directory structure
            String baseDir = System.getProperty("user.dir") + "/generated-projects/";
            File directory = new File(baseDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Prepare project-specific details
            String appId = aemProjectModel.getProjectName().toLowerCase().replace(" ", "-");

            // Prepare Maven command for clean 6.5.13 structure
            String command = String.format(
                    "mvn -B org.apache.maven.plugins:maven-archetype-plugin:3.2.1:generate " +
                            "-DarchetypeGroupId=com.adobe.aem " +
                            "-DarchetypeArtifactId=aem-project-archetype " +
                            "-DarchetypeVersion=41 " +
                            "-DappTitle=\"%s\" " +
                            "-DappId=\"%s\" " +
                            "-DgroupId=\"%s\" " +
                            "-DaemVersion=\"%s\" " +
                            "-Darchetype.interactive=false " +
                            "-DincludeDispatcherConfig=y " +
                            "-DincludeDispatcherCloud=n " +
                            "-DincludeDispatcherAMS=n " + // You can change to "n" if you don't want AMS either
                            "-DincludeFrontendModuleGeneral=n " +
                            "-DincludeFrontendModuleReact=n " +
                            "-DincludeFrontendModuleAngular=n " +
                            "-DincludeFrontendModuleReactFormsAF=n " +
                            "-DincludeCommerce=n " +
                            "-DincludeCommerceFrontend=n " +
                            "-Dlanguage=en " +
                            "-Dcountry=us " +
                            "-DsingleCountry=n",
                    aemProjectModel.getProjectName(),
                    appId,
                    aemProjectModel.getPackageName(),
                    aemProjectModel.getVersion()
            );

            // OS-specific ProcessBuilder (cross-platform)
            ProcessBuilder processBuilder;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                processBuilder = new ProcessBuilder("bash", "-c", command);
            }

            // Set working directory
            processBuilder.directory(directory);

            // Stream logs to console for debugging
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.getInputStream().transferTo(System.out);

            int exitCode = process.waitFor();

            // Output Results
            if (exitCode == 0) {
                System.out.println("AEM project generated successfully.");
            } else {
                System.out.println(" AEM project generation failed with exit code: " + exitCode);
            }

            // Step 1: Copy selected components to ui.apps
            String componentsTargetPath = baseDir + appId + "/ui.apps/src/main/content/jcr_root/apps/" + appId + "/components/content/";
            File contentFolder = new File(componentsTargetPath);
            if (!contentFolder.exists()) {
                contentFolder.mkdirs(); // Create the content folder
            }
            componentService.copySelectedComponents(aemProjectModel.getSelectedComponents(), componentsTargetPath);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }


}