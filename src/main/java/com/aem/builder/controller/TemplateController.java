package com.aem.builder.controller;

import com.aem.builder.model.TemplateModel;
import com.aem.builder.service.ComponentService;
import com.aem.builder.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aem.builder.constants.UrlMappings.*;
import static com.aem.builder.constants.UrlMappings.CREATE_TEMPLATE;
import static com.aem.builder.constants.ViewNames.*;

@Slf4j
@Controller
@RequiredArgsConstructor

public class TemplateController {
    private final TemplateService templateService;
    private final ComponentService componentService;
    @GetMapping(FETCH_TEMPLATES)
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

    @PostMapping(ADD_TEMPLATE)
    public String addTemplateToExistingProject(@PathVariable String projectname, @RequestBody List<String> templatelist) {

        log.info(projectname);
        log.info(templatelist.toString());
        try {
            templateService.copySelectedTemplatesToGeneratedProject(projectname, templatelist);
            return DASHBOARD;

        } catch (IOException e) {
            return CREATE;

        }
    }

    // creating template
    @PostMapping(CREATE_TEMPLATE)
    public ResponseEntity<String> createTemplate(@PathVariable String projectname, @RequestBody TemplateModel model) {
        List<String> projectTemplates = templateService.getTemplateNamesFromDestination(projectname);

        if (projectTemplates.contains(model.getName())) {
            return ResponseEntity.ok("Template already exists");
        }
        else {
            try {
                    TemplateModel resutlmodel   = templateService.createTemplate(model, projectname);
                    System.out.println(resutlmodel.toString());

                return ResponseEntity.ok("Template generated successfully: " + model.getName());
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(500).body("Error generating template: " + e.getMessage());
            }
        }
    }

    @GetMapping(CREATE_TEMPLATE_GET)
    public String showCreateTemplateForm(@PathVariable String projectName, Model model) {
        model.addAttribute("projectName", projectName);

        return CREATE_TEMPLATE_VIEW;
    }

    @GetMapping(LIST_TEMPLATES)
    public ResponseEntity<List<String>> listTemplates(@PathVariable String projectname) {
        List<String> templates = templateService.getTemplateNamesFromDestination(projectname);
        return ResponseEntity.ok(templates);
    }
    @GetMapping(TEMPLATE_TYPES)
    public ResponseEntity<List<String>> getTemplateTypes(@PathVariable String projectName) {
        List<String> templateTypes = templateService.getTemplateTypesFromDestination(projectName);
        if (templateTypes.isEmpty()) {
            return ResponseEntity.noContent().build(); // or return empty list with 200
        }

        return ResponseEntity.ok(templateTypes);
    }
    // updating template
    @GetMapping(EDIT_TEMPLATE )
    public String showEditTemplateForm(@RequestParam String templateName,
                                       @PathVariable String projectName,
                                       Model model) {
        TemplateModel templateModel = templateService.loadTemplateByName(projectName, templateName);
        log.info("[showEditTemplateForm] "+templateModel.toString());
        if (templateModel == null) {
            model.addAttribute("error", "Template not found or unreadable.");
            return REDIRECT_VIEW_PREFIX+ projectName ;
        }

        model.addAttribute("template", templateModel);
        model.addAttribute("tempname",templateName);
        model.addAttribute("editMode", true);
        model.addAttribute("projectName", projectName);
        return TEMPLATE_UI;
    }

    @PostMapping(UPDATE_TEMPLATE )
    public String updateTemplate(@ModelAttribute("template") TemplateModel template,
                                 @PathVariable String projectname,@PathVariable String templateName,
                                 Model model) {
        try {
            templateService.updateTemplate(template,projectname,templateName);
            log.info(template.toString());
            log.info("[updateTemplate]"+projectname);
            return REDIRECT_VIEW_PREFIX+ projectname ;
        } catch (Exception e) {
            model.addAttribute("error", "Template update failed: " + e.getMessage());
            return CREATE_TEMPLATE_VIEW;
        }
    }



}







