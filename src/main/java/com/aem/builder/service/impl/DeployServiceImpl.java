package com.aem.builder.service.impl;

import com.aem.builder.service.DeployService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;

@Service
public class DeployServiceImpl implements DeployService {

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public Flux<String> deployProjectLive(String projectName) {
        return Flux.<String>create(emitter -> {
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    File projectDir = new File(PROJECTS_DIR, projectName);
                    if (!projectDir.exists()) {
                        emitter.next("❌ Project directory does not exist: " + projectDir.getAbsolutePath());
                        emitter.complete();
                        return;
                    }
                    ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "install", "-PautoInstallPackage");
                    pb.directory(projectDir);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    String finalStatus = "";

                    while ((line = reader.readLine()) != null) {
                        // Emit INFO, WARN, ERROR live
                        if (line.contains("[INFO]") || line.contains("[WARN]") || line.contains("[ERROR]")) {
                            emitter.next(line);
                        }
                        // Capture build status
                        if (line.toLowerCase().contains("build success")) {
                            finalStatus = "✅ Build successful for project: " + projectName;
                        } else if (line.toLowerCase().contains("build failed")) {
                            finalStatus = "❌ Build failed for project: " + projectName;
                        }
                    }
                    int exitCode = process.waitFor();
                    if (exitCode != 0 && finalStatus.isEmpty()) {
                        finalStatus = "❌ Build failed for project: " + projectName;
                    }
                    // Emit final status
                    emitter.next(finalStatus);
                    emitter.complete();

                } catch (Exception e) {
                    emitter.next("❌ Exception during build: " + e.getMessage());
                    emitter.complete();
                }
            });
        }).delayElements(Duration.ofMillis(10));
    }
}
