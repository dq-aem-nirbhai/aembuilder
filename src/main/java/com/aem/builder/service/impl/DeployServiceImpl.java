package com.aem.builder.service.impl;

import com.aem.builder.service.DeployService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

@Service
public class DeployServiceImpl implements DeployService {

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public String deployProject(String projectName) throws Exception {
        File projectDir = new File(PROJECTS_DIR, projectName);
        ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "install", "-PautoInstallPackage");
        pb.directory(projectDir);
        pb.redirectErrorStream(true); // stderr + stdout combined

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            // Extract meaningful part of the failure to show the user
            String meaningfulError = extractErrorMessage(output.toString());
            throw new RuntimeException(meaningfulError);
        }

        return "Build successful for project: " + projectName;
    }

    @Override
    public String extractErrorMessage(String fullOutput) {
        StringBuilder errorOutput = new StringBuilder();
        String[] lines = fullOutput.split("\\R");

        for (String line : lines) {
            if (line.contains("[ERROR]")) {
                errorOutput.append(line.replace("[ERROR]", "").trim()).append(System.lineSeparator());
            }
        }

        String extracted = errorOutput.toString().trim();
        if (extracted.isEmpty()) {
            return "Build failed, but no specific [ERROR] message found. See logs.";
        }
        return extracted;
    }
}
