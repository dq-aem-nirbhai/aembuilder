package com.aem.builder.controller;
 
import com.aem.builder.service.ToolService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.aem.builder.constants.UrlMappings.*;

@Slf4j

@Controller

@RequestMapping(TOOL)

public class ToolController {
 
    @Autowired

    private ToolService toolService;


    @GetMapping(FETCH_TOOLS)
    @ResponseBody
    public List<String> fetchTools(@PathVariable String projectname) {
        log.info("[ToolController] Fetching available tools for '{}'", projectname);

        List<String> toolNames = toolService.fetchTools(projectname);
        return toolNames;
    }

    @PostMapping(ADD_TOOL)
    @ResponseBody
    public String addToolsToExistingProject(
            @PathVariable String projectname,
            @RequestBody List<String> selectedTools) {

        log.info("[ToolController] Adding {} tool(s) to project '{}'", selectedTools.size(), projectname);
        try {
            toolService.addToolsToExistingProject(projectname, selectedTools);
            return "OK";
        } catch (Exception e) {
            log.error("[ToolController] Error adding tools: {}", e.getMessage(), e);
            return "ERROR";
        }
    }

@GetMapping(EXISTING_TOOLS)
@ResponseBody
public List<String>getExistingToolOfProject(@PathVariable String projectname){

            List<String> existingTools = toolService.getExistingTools(projectname);
        return existingTools;

}

}

 