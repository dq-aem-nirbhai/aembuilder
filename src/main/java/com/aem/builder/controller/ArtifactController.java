package com.aem.builder.controller;

import com.aem.builder.service.ArtifactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Controller for handling Artifact Library operations and the dynamic artifact generator page.
 * This version is focused on dynamic generation (no modal-based copying). The UI opens a separate page
 * where the user chooses an artifact type and submits parameters; the backend writes Java files into
 * the selected generated-projects/{projectName}/core module.
 */
@Controller
@RequestMapping("/{projectName}/artifacts")
public class ArtifactController {

    @Autowired
    private ArtifactService artifactService;

    // Show artifacts (info-only); marks Already Exists when a file exists in target project
    @GetMapping
    public String showArtifacts(@PathVariable String projectName, Model model) {
        try {
            Map<String, List<ArtifactService.ArtifactFile>> artifactMap = artifactService.listArtifacts();
            Path coreJavaBase = Paths.get("generated-projects", projectName, "core/src/main/java");
            for (var entry : artifactMap.entrySet()) {
                for (ArtifactService.ArtifactFile file : entry.getValue()) {
                    Path relativePath = Paths.get(file.relativePath);
                    String subFolder = detectTargetFolder(relativePath.toString());
                    Path targetPath = coreJavaBase.resolve("com/aem/" + projectName + "/core")
                            .resolve(subFolder)
                            .resolve(file.name);
                    if (Files.exists(targetPath)) {
                        file.packageInfo = file.packageInfo + " (Already Exists)";
                    }
                }
            }
            model.addAttribute("projectName", projectName);
            model.addAttribute("artifactMap", artifactMap);
        } catch (IOException e) {
            model.addAttribute("message", "Error loading artifact list: " + e.getMessage());
            model.addAttribute("artifactMap", Collections.emptyMap());
        }
        return "artifacts";
    }

    // Show the page for creating a new artifact (dynamic form)
    @GetMapping("/artifact-generator")
    public String showArtifactGeneratorPage(@PathVariable String projectName, Model model) {
        model.addAttribute("projectName", projectName);
        return "artifact-generator";
    }

    // Handle the create form submission (redirects back to artifacts page with flash message)
    @PostMapping("/create")
    public String createDynamicArtifact(
            @PathVariable String projectName,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {

        String type = params.get("type");
        if (type == null || type.isBlank()) {
            redirectAttributes.addFlashAttribute("message", "Artifact type is required.");
            return "redirect:/" + projectName + "/artifacts";
        }

        try {
            artifactService.generateDynamicArtifact(projectName, type, params);
            redirectAttributes.addFlashAttribute("message", "✅ " + type + " created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message", "❌ Failed to create " + type + ": " + e.getMessage());
        }

        return "redirect:/" + projectName + "/artifacts";
    }

    // helper to keep service and UI consistent
    private String detectTargetFolder(String relativePath) {
        relativePath = relativePath.toLowerCase();
        if (relativePath.contains("servlet")) return "servlets";
        if (relativePath.contains("handler")) return "handlers";
        if (relativePath.contains("model")) return "models";
        if (relativePath.contains("service")) return "services";
        if (relativePath.contains("listener")) return "listeners";
        if (relativePath.contains("scheduler")) return "schedulers";
        if (relativePath.contains("filter")) return "filters";
        if (relativePath.contains("workflow")) return "workflows";
        return "misc";
    }
}
