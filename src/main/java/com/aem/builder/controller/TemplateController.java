package com.aem.builder.controller;

import com.aem.builder.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor

public class TemplateController {
    private final TemplateService templateService;

    @GetMapping("/fetch-templates/{projectname}")
    @ResponseBody
    public Map<String, List<String>> getTemplates(@PathVariable String projectname, Model model) throws IOException {
        List<String> resourcetemplates = templateService.getTemplateFileNames();
        List<String> projectTemplates = templateService.getTemplateNamesFromDestination(projectname);
        List<String> distinct = templateService.getDistinctTemplates(projectname, resourcetemplates, projectTemplates);
        List<String> common = templateService.getCommonTemplates(resourcetemplates, projectTemplates);
        Map<String, List<String>> response = new HashMap<>();
        response.put("unique", distinct);
        response.put("duplicate", common);
        return response;
    }

    @PostMapping("/add-template/{projectname}")
    public String addTemplateToExistingProject(@PathVariable String projectname,
            @RequestBody List<String> templatelist) {

        log.info(projectname);
        log.info(templatelist.toString());
        try {
            templateService.copySelectedTemplatesToGeneratedProject(projectname, templatelist);
            return "dashboard";

        } catch (IOException e) {
            return "create";

        }
    }











}
