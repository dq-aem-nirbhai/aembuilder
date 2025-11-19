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
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link DeployService} for deploying AEM projects.
 * Streams build logs live using Reactor {@link Flux}.
 */
@Slf4j
@Service
public class DeployServiceImpl implements DeployService {

    @Override
    public Flux<String> deployProjectLive(String projectName, String deployType) {
        log.info("{} Starting deployment for '{}' with type '{}'", DEPLOY_LOG_PREFIX, projectName, deployType);

        return Flux.<String>create(emitter -> {
            Schedulers.boundedElastic().schedule(() -> {
                BufferedReader reader = null;
                try {
                    File projectRoot = new File(PROJECTS_DIR, projectName);
                    if (!projectRoot.exists()) {
                        emitter.next("ERROR: Project directory does not exist: " + projectRoot.getAbsolutePath());
                        emitter.complete();
                        log.warn("[deployProjectLive] Project directory does not exist: {}", projectRoot.getAbsolutePath());
                        return;
                    }

                    // default working dir = project root
                    File workingDir = projectRoot;
                    List<String> command = new ArrayList<>();
                    command.add("mvn");
                    command.add("clean");
                    command.add("install");

                    // choose command and working directory
                    if ("core".equalsIgnoreCase(deployType)) {
                        // Prefer building only core module if it exists
                        File coreDir = new File(projectRoot, "core");
                        if (coreDir.exists() && coreDir.isDirectory()) {
                            workingDir = coreDir;
                            emitter.next("INFO: Found core module at: " + coreDir.getAbsolutePath());
                        } else {
                            emitter.next("WARN: core module not found at " + coreDir.getAbsolutePath() + " — running bundle profile from project root.");
                            workingDir = projectRoot;
                        }
                        command.add("-PautoInstallBundle");
                        emitter.next("INFO: Using profile: -PautoInstallBundle");
                    } else if ("ui".equalsIgnoreCase(deployType) || "ui.content".equalsIgnoreCase(deployType)) {
                        File uiContent = new File(projectRoot, "ui.content");
                        if (!uiContent.exists() || !uiContent.isDirectory()) {
                            emitter.next("ERROR: ui.content module directory not found: " + uiContent.getAbsolutePath());
                            emitter.complete();
                            log.warn("[deployProjectLive] ui.content module missing: {}", uiContent.getAbsolutePath());
                            return;
                        }
                        workingDir = uiContent;
                        command.add("-PautoInstallBundle");
                        emitter.next("INFO: UI content deploy — working directory: " + uiContent.getAbsolutePath());
                        emitter.next("INFO: Using profile: -PautoInstallBundle");
                    } else {
                        // full
                        command.add("-PautoInstallPackage");
                        emitter.next("INFO: Using profile: -PautoInstallPackage (full deploy from project root)");
                    }

                    // Validate working directory exists
                    if (!workingDir.exists() || !workingDir.isDirectory()) {
                        emitter.next("ERROR: Working directory does not exist: " + workingDir.getAbsolutePath());
                        emitter.complete();
                        return;
                    }

                    emitter.next("INFO: Starting Maven in: " + workingDir.getAbsolutePath());
                    emitter.next("INFO: Command: " + String.join(" ", command));

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.directory(workingDir);
                    pb.redirectErrorStream(true);

                    Process process = pb.start();
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    String finalStatus = "";

                    while ((line = reader.readLine()) != null) {
                        // Emit only INFO/WARN/ERROR lines to keep stream clean
                        if (line.contains(INFO_LOG) || line.contains(WARN_LOG) || line.contains(ERROR_LOG)) {
                            emitter.next(line);
                        }
                        // Capture success/failure keywords
                        String lower = line.toLowerCase();
                        if (lower.contains(BUILD_SUCCESS_KEYWORD)) {
                            finalStatus = "Build successful for project: " + projectName;
                        } else if (lower.contains(BUILD_FAILED_KEYWORD)) {
                            finalStatus = "Build failed for project: " + projectName;
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0 && finalStatus.isEmpty()) {
                        finalStatus = "Build failed for project: " + projectName + " (exit " + exitCode + ")";
                    }

                    emitter.next(finalStatus);
                    emitter.complete();
                    log.info("[deployProjectLive] Deployment completed for '{}', type={}", projectName, deployType);

                } catch (Exception e) {
                    emitter.next("ERROR: Exception during deployment: " + e.getMessage());
                    emitter.complete();
                    log.error("[deployProjectLive] Error during deployment for '{}', type={}", projectName, deployType, e);
                } finally {
                    try { if (reader != null) reader.close(); } catch (Exception ex) { /* ignore */ }
                }

            });
        }).delayElements(Duration.ofMillis(10));
    }
}
