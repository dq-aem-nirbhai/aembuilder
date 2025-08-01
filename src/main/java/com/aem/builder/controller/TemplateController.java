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

@Slf4j
@Controller
@RequiredArgsConstructor

public class TemplateController {
    private final TemplateService templateService;
    private final ComponentService componentService;
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
    public String addTemplateToExistingProject(@PathVariable String projectname, @RequestBody List<String> templatelist) {

        log.info(projectname);
        log.info(templatelist.toString());
        try {
            templateService.copySelectedTemplatesToGeneratedProject(projectname, templatelist);
            return "dashboard";

        } catch (IOException e) {
            return "create";

        }
    }


    // creating template
    @PostMapping("/create-template/{projectname}")
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

    @GetMapping("/{projectName}/createtemplate")
    public String showCreateTemplateForm(@PathVariable String projectName, Model model) {
        model.addAttribute("projectName", projectName);

        return "createtemplate";
    }



    @GetMapping("/templates/list/{projectname}")
    public ResponseEntity<List<String>> listTemplates(@PathVariable String projectname) {
        List<String> templates = templateService.getTemplateNamesFromDestination(projectname);
        return ResponseEntity.ok(templates);
    }
    @GetMapping("/template-types/{projectName}")
    public ResponseEntity<List<String>> getTemplateTypes(@PathVariable String projectName) {
        List<String> templateTypes = templateService.getTemplateTypesFromDestination(projectName);
        System.out.println(templateTypes);
        if (templateTypes.isEmpty()) {
            return ResponseEntity.noContent().build(); // or return empty list with 200
        }

        return ResponseEntity.ok(templateTypes);
    }
    // updating template
    @GetMapping("/{projectName}/edittemplate")
    public String showEditTemplateForm(@RequestParam String templateName,
                                       @PathVariable String projectName,
                                       Model model) {
        TemplateModel templateModel = templateService.loadTemplateByName(projectName, templateName);

        System.out.println(templateModel+"*********************");
        if (templateModel == null) {
            model.addAttribute("error", "Template not found or unreadable.");
            return "redirect:/" + projectName ;
        }

        model.addAttribute("template", templateModel);
        model.addAttribute("tempname",templateName);
        System.out.println(templateModel);
        model.addAttribute("editMode", true);
        model.addAttribute("projectName", projectName);
        return "template-ui";
    }



    @PostMapping("/{projectname}/updatetemplate/{templateName}")
    public String updateTemplate(@ModelAttribute("template") TemplateModel template,
                                 @PathVariable String projectname,@PathVariable String templateName,
                                 Model model) {
        System.out.println(template.toString()+"uuuuuuuuuuuu");
        try {
           // TemplateModel updatedatetemplate = templateService.updatedTemplateModel(template, projectname);
            templateService.updateTemplate(template,projectname,templateName);
            return "redirect:/" + projectname ;
        } catch (Exception e) {
            model.addAttribute("error", "Template update failed: " + e.getMessage());
            return "createtemplate";
        }
    }



}







