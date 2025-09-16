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

/**
 * Implementation of {@link DeployService} for deploying AEM projects.
 * Streams build logs live using Reactor {@link Flux}.
 */
@Slf4j
@Service
public class DeployServiceImpl implements DeployService {

    /**
     * Deploys the given project and streams logs live.
     *
     * @param projectName the name of the project to deploy
     * @return Flux<String> emitting deployment logs in real-time
     */
    @Override
    public Flux<String> deployProjectLive(String projectName) {
        log.info("{} Starting deployment for '{}'", DEPLOY_LOG_PREFIX, projectName);

        return Flux.<String>create(emitter -> {
            Schedulers.boundedElastic().schedule(() -> {

                BufferedReader reader = null;

                try {
                    File projectDir = new File(PROJECTS_DIR, projectName);
                    if (!projectDir.exists()) {
                        emitter.next("Project directory does not exist: " + projectDir.getAbsolutePath());
                        emitter.complete();
                        log.warn("[deployProjectLive] Project directory does not exist: {}", projectDir.getAbsolutePath());
                        return;
                    }

                    // Prepare Maven command
                    ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "install", "-PautoInstallPackage");
                    pb.directory(projectDir);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    // Read output stream
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    String finalStatus = "";

                    while ((line = reader.readLine()) != null) {
                        // Emit only INFO, WARN, ERROR logs
                        if (line.contains(INFO_LOG) || line.contains(WARN_LOG) || line.contains(ERROR_LOG)) {
                            emitter.next(line);
                        }

                        // Capture build status
                        String lineLower = line.toLowerCase();
                        if (lineLower.contains(BUILD_SUCCESS_KEYWORD)) {
                            finalStatus = "Build successful for project: " + projectName;
                        } else if (lineLower.contains(BUILD_FAILED_KEYWORD)) {
                            finalStatus = "Build failed for project: " + projectName;
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0 && finalStatus.isEmpty()) {
                        finalStatus = "Build failed for project: " + projectName;
                    }

                    emitter.next(finalStatus);
                    emitter.complete();
                    log.info("[deployProjectLive] Deployment completed for '{}'", projectName);

                } catch (Exception e) {
                    String errorMessage = "Exception during deployment: " + e.getMessage();
                    emitter.next(errorMessage);
                    emitter.complete();
                    log.error("[deployProjectLive] Error during deployment for '{}'", projectName, e);

                } finally {
                    // Safely close resources
                    try {
                        if (reader != null) reader.close();
                        log.debug("[deployProjectLive] BufferedReader closed for '{}'", projectName);
                    } catch (Exception e) {
                        log.error("[deployProjectLive] Failed to close BufferedReader for '{}'", projectName, e);
                    }
                }
            });
        }).delayElements(Duration.ofMillis(10)); // throttle emission slightly for frontend
    }
}
