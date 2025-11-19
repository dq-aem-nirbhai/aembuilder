package com.aem.builder.controller;

import static com.aem.builder.constants.AemProjectConstants.PROJECTS_DIR;
import static com.aem.builder.constants.ModelAttributeKeys.*;
import static com.aem.builder.constants.UrlMappings.*;
import static com.aem.builder.constants.ViewNames.*;

import com.aem.builder.service.DeployService;
import com.aem.builder.service.GitBranchService;
import com.aem.builder.service.impl.ComponentServiceImpl;
import com.aem.builder.service.impl.TemplateServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Controller responsible for handling deployment-related views and actions.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DeployController {

    private final ComponentServiceImpl componentService;
    private final TemplateServiceImpl templateService;
    private final DeployService deployService;
    private final GitBranchService gitBranchService;

    @GetMapping(VIEW_PROJECT_URL)
    public String projectDetails(@PathVariable String projectName, Model model) {
        log.info("[projectDetails] Fetching project details for '{}'", projectName);

        List<String> templates = Collections.emptyList();
        Map<String, String> compMap = Collections.emptyMap();

        try {
            templates = templateService.fetchTemplatesFromGeneratedProjects(projectName);
        } catch (Exception e) {
            log.error("[projectDetails] Failed to fetch templates for '{}'", projectName, e);
        }

        try {
            compMap = componentService.fetchComponentsWithGroups(projectName);
        } catch (Exception e) {
            log.error("[projectDetails] Failed to fetch components for '{}'", projectName, e);
        }

        if ((templates == null || templates.isEmpty()) &&
                (compMap == null || compMap.isEmpty())) {
            log.error("[projectDetails] No project found for '{}'", projectName);
            model.addAttribute(ERROR,
                    "Project '" + projectName + "' not found or has no data.");
            return ERROR;
        }

        List<String> components = new ArrayList<>(compMap.keySet());
        String appTitle;
        try {
            appTitle = componentService.readAppTitleFromPom(projectName);
        } catch (Exception e) {
            log.warn("[projectDetails] Failed to read app title from POM for '{}', using fallback", projectName, e);
            appTitle = projectName;
        }

        List<String> editable = componentService.getEditableComponents(compMap, appTitle);
        log.info("[projectDetails] Editable components for '{}': {}", projectName, editable);

        Path projectPath = Paths.get(PROJECTS_DIR, projectName);
        String branch = "unknown";
        List<String> branches = Collections.emptyList();
        boolean hasStash = false;
        try {
            branch = gitBranchService.getCurrentBranch(projectPath);
            branches = gitBranchService.listBranches(projectPath);
            hasStash = gitBranchService.hasStash(projectPath);
        } catch (Exception e) {
            log.error("[projectDetails] Error fetching Git details for '{}'", projectName, e);
        }

        model.addAttribute(PROJECT_NAME, projectName);
        model.addAttribute(BRANCH, branch);
        model.addAttribute(BRANCHES, branches);
        model.addAttribute(HAS_STASH, hasStash);
        model.addAttribute(COMPONENTS, components);
        model.addAttribute(EDITABLE_COMPONENTS, editable);
        model.addAttribute(TEMPLATES, templates);
        model.addAttribute(CAN_DEPLOY, true);

        log.debug("[projectDetails] Model attributes set for '{}'", projectName);
        return DEPLOY_PAGE;
    }

    /**
     * Render deploy logs page only (no streaming here). Accepts 'type' query param.
     */
    @GetMapping(DEPLOY_PROJECT_URL)
    public String deployProject(@PathVariable String projectName,
                                @RequestParam(name = "type", defaultValue = "full") String type,
                                Model model) {
        log.info("[deployProject] Opening deploy logs page for '{}' with type '{}'", projectName, type);
        try {
            model.addAttribute(PROJECT_NAME, projectName);
            model.addAttribute("deployType", type);
        } catch (Exception e) {
            log.error("[deployProject] Failed to prepare deploy logs page for '{}'", projectName, e);
            model.addAttribute(ERROR, "Unable to open deployment logs for project: " + projectName);
            return ERROR_PAGE;
        }
        return DEPLOY_LOGS_PAGE;
    }

    /**
     * SSE endpoint that actually starts the build.
     */
    @GetMapping(value = DEPLOY_LOGS_URL, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamLogs(@PathVariable String projectName,
                                   @RequestParam(name = "type", defaultValue = "full") String type) {
        log.info("[streamLogs] Streaming logs for '{}' with type '{}'", projectName, type);
        try {
            return deployService.deployProjectLive(projectName, type)
                    .doOnComplete(() -> log.info("[streamLogs] Completed log streaming for '{}' (type={})", projectName, type))
                    .doOnError(err -> log.error("[streamLogs] Error while streaming logs for '{}'", projectName, err));
        } catch (Exception e) {
            log.error("[streamLogs] Failed to initiate log streaming for '{}'", projectName, e);
            return Flux.just("Error: Unable to stream logs for project " + projectName);
        }
    }
}
