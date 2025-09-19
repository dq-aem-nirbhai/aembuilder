package com.aem.builder.controller;

import com.aem.builder.service.GitBranchService;
import com.aem.builder.service.impl.ComponentServiceImpl;
import com.aem.builder.service.impl.DeployServiceImpl;
import com.aem.builder.service.impl.TemplateServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DeployController {


    private final ComponentServiceImpl componentService;
    private final TemplateServiceImpl templateService;
    private final DeployServiceImpl deployService;
    private final GitBranchService gitBranchService;

    private static final String PROJECTS_DIR = "generated-projects";


    @GetMapping("/view/{projectName}")
    public String projectDetails(@PathVariable String projectName, Model model) {
        log.info("DEPLOY: Fetching project details for project: {}", projectName);
        List<String> templates = templateService.fetchTemplatesFromGeneratedProjects(projectName);
        Map<String, String> compMap = componentService.fetchComponentsWithGroups (projectName);

        // If project not found or no components/templates
        if ((templates == null || templates.isEmpty()) &&
                (compMap == null || compMap.isEmpty())) {
            log.error("DEPLOY: No project found for name '{}'", projectName);
            model.addAttribute("errorMessage", "Project '" + projectName + "' not found or has no data.");
            return "error"; // forward to error.html (or error.jsp depending on your setup)
        }

        List<String> components = new ArrayList<>(compMap.keySet());
        String appTitle = componentService.readAppTitleFromPom(projectName);

        // Fallback to appName if title not found
        if (appTitle == null || appTitle.isBlank()) {
            appTitle = projectName;
        }

        final String finalAppTitle = appTitle;

        List<String> editable = compMap.entrySet().stream()
                .filter(e -> {
                    String g = e.getValue();
                    if (g != null) {
                        g = g.trim();
                    }
                    return g == null
                            || (!g.equals(finalAppTitle + " - Structure")
                            && !g.equals(".hidden"));
                })
                .map(Map.Entry::getKey)
                .toList();

        log.info("Editable {}", editable);

        log.info("Fetching Git related details..");
        Path projectPath = Paths.get(PROJECTS_DIR, projectName);
        String branch = gitBranchService.getCurrentBranch(projectPath);
        List<String> branches = gitBranchService.listBranches(projectPath);
        log.info("Fetched Git related details..");

        model.addAttribute("projectName", projectName);
        model.addAttribute("branch", branch);
        model.addAttribute("branches", branches);

        try {
            model.addAttribute("hasStash", gitBranchService.hasStash(projectPath));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        model.addAttribute("components", components);
        model.addAttribute("editableComponents", editable);
        model.addAttribute("templates", templates);
        model.addAttribute("projectName", projectName);
        model.addAttribute("canDeploy", true);

        log.debug("DEPLOY: Added attributes to model for project: {}", projectName);
        return "deploy";
    }

    @GetMapping("/{projectName}/deploy")
    public String deployProject(@PathVariable String projectName, Model model) {
        model.addAttribute("projectName", projectName);
        return "deployLogs";
    }

    @GetMapping(value = "/{projectName}/deploy/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamLogs(@PathVariable String projectName) {
        return deployService.deployProjectLive(projectName);
    }

}