package com.aem.builder.controller;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.service.TemplateService;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor

public class TemplateController {
    private final TemplateService templateService;

    @GetMapping("fetch-templates/{projectname}")
    public String getTemplates(@PathVariable String projectname, Model model) throws IOException {
        List<String> resourcetemplates = templateService.getTemplateFileNames();
        List<String>projectTemplates=templateService.getTemplateNamesFromDestination(projectname);
      List<String>distinct=templateService.getDistinctTemplates(projectname,resourcetemplates,projectTemplates);
      List<String>common=templateService.getCommonTemplates(resourcetemplates,projectTemplates);
        model.addAttribute("common", common);
        model.addAttribute("distinct",distinct);
        return "dashboard";
    }
}
