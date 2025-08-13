package com.aem.builder.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Flux;

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

    @GetMapping("/view/{projectName}")
    public String projectDetails(@PathVariable String projectName, Model model) {
        log.info("DEPLOY: Fetching project details for project: {}", projectName);
        List<String> templates = templateService.fetchTemplatesFromGeneratedProjects(projectName);
        Map<String, String> compMap = componentService.fetchComponentsWithGroups(projectName);


        List<String> components = new ArrayList<>(compMap.keySet());

        String appName = componentService.getAppName(projectName);
        String appTitle = componentService.readAppTitleFromPom(projectName);

// Fallback to appName if title not found
        if (appTitle == null || appTitle.isBlank()) {
            appTitle = appName;
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



        log.info("Editable {}",editable);


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