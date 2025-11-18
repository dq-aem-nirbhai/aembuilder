package com.aem.builder.service.impl;

import static com.aem.builder.constants.AemProjectConstants.PROJECTS_DIR;
import static com.aem.builder.constants.DeployConstants.*;

import com.aem.builder.service.DeployService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;

@Slf4j
@Service
public class DeployServiceImpl implements DeployService {

    @Override
    public Flux<String> deployProjectLive(String projectName, String deployType) {

        log.info("{} Starting deployment for '{}' with type '{}'",
                DEPLOY_LOG_PREFIX, projectName, deployType);

        return Flux.<String>create(emitter -> {
            Schedulers.boundedElastic().schedule(() -> {

                BufferedReader reader = null;

                try {
                    File projectDir = new File(PROJECTS_DIR, projectName);

                    if (!projectDir.exists()) {
                        emitter.next("Project directory does not exist: " + projectDir.getAbsolutePath());
                        emitter.complete();
                        return;
                    }

                    // Decide command + working directory
                    ProcessBuilder pb;
                    File workingDir;

                    if ("core".equalsIgnoreCase(deployType)) {
                        workingDir = new File(projectDir, "core");
                        pb = new ProcessBuilder("mvn", "clean", "install", "-PautoInstallBundle");
                        emitter.next("Running CORE deployment...");
                    } else {
                        workingDir = projectDir;
                        pb = new ProcessBuilder("mvn", "clean", "install", "-PautoInstallPackage");
                        emitter.next("Running FULL deployment...");
                    }

                    if (!workingDir.exists()) {
                        emitter.next("Module directory does not exist: " + workingDir.getAbsolutePath());
                        emitter.complete();
                        return;
                    }

                    pb.directory(workingDir);
                    pb.redirectErrorStream(true);

                    Process process = pb.start();
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    String finalStatus = "";

                    while ((line = reader.readLine()) != null) {
                        if (line.contains(INFO_LOG) || line.contains(WARN_LOG) || line.contains(ERROR_LOG)) {
                            emitter.next(line);
                        }

                        String l = line.toLowerCase();
                        if (l.contains(BUILD_SUCCESS_KEYWORD)) {
                            finalStatus = "Build successful for project: " + projectName;
                        } else if (l.contains(BUILD_FAILED_KEYWORD)) {
                            finalStatus = "Build failed for project: " + projectName;
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0 && finalStatus.isEmpty()) {
                        finalStatus = "Build failed for project: " + projectName;
                    }

                    emitter.next(finalStatus);
                    emitter.complete();

                } catch (Exception e) {
                    emitter.next("Exception during deployment: " + e.getMessage());
                    emitter.complete();
                } finally {
                    try {
                        if (reader != null) reader.close();
                    } catch (Exception ignored) {}
                }
            });
        }).delayElements(Duration.ofMillis(10));
    }
}
