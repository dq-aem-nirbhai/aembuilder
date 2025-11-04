package com.aem.builder.controller;
 
import com.aem.builder.service.ToolService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
 
@Slf4j

@Controller

@RequestMapping("/tools")

public class ToolController {
 
    @Autowired

    private ToolService toolService;
 
    private static final String DASHBOARD_PAGE = "redirect:/dashboard";

    private static final String PROJECT_PAGE = "redirect:/projectdetails";

    @GetMapping("/fetchtools/{projectname}")
    @ResponseBody
    public List<String> fetchTools(@PathVariable("projectname") String projectName) {
        log.info("[ToolController] Fetching available tools for '{}'", projectName);

        String toolsBasePath = System.getProperty("user.dir")
                + "/src/main/resources/excel-importer-tool";

        File baseDir = new File(toolsBasePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn("[ToolController] Tools directory not found at {}", toolsBasePath);
            return List.of();
        }

        // List only folder names
        File[] dirs = baseDir.listFiles(File::isDirectory);
        List<String> toolNames = dirs != null
                ? Arrays.stream(dirs).map(File::getName).toList()
                : List.of();
              log.info("[ToolController] tools found -> {}",toolNames);
        log.info("[ToolController] Found {} tool(s)", toolNames.size());
        return toolNames;
    }

    @PostMapping("/add/{projectname}")
    @ResponseBody
    public String addToolsToExistingProject(
            @PathVariable("projectname") String projectName,
            @RequestBody List<String> selectedTools) {

        log.info("[ToolController] Adding {} tool(s) to project '{}'", selectedTools.size(), projectName);
        try {
            toolService.addToolsToExistingProject(projectName, selectedTools);
            return "OK";
        } catch (Exception e) {
            log.error("[ToolController] Error adding tools: {}", e.getMessage(), e);
            return "ERROR";
        }
    }

@GetMapping("/existingtools/{projectname}")
@ResponseBody
public List<String>getExistingToolOfProject(@PathVariable("projectname") String projectName){

            List<String> existingTools = toolService.getExistingTools(projectName);
        return existingTools;

}

}

 