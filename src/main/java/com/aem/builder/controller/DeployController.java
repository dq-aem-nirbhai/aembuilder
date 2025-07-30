package com.aem.builder.controller;

import com.aem.builder.service.impl.ComponentServiceImpl;
import com.aem.builder.service.impl.DeployServiceImpl;
import com.aem.builder.service.impl.TemplateServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequiredArgsConstructor
public class DeployController {

    private static final Logger logger = LoggerFactory.getLogger(DeployController.class);

    private final ComponentServiceImpl componentService;
    private final TemplateServiceImpl templateService;
    private final DeployServiceImpl deployService;

    @GetMapping("/{projectName}")
    public String projectDetails(@PathVariable String projectName, Model model) {
        logger.info("DEPLOY: Fetching project details for project: {}", projectName);
        List<String> components = componentService.fetchComponentsFromGeneratedProjects(projectName);
        List<String> templates = templateService.fetchTemplatesFromGeneratedProjects(projectName);
        model.addAttribute("components", components);
        model.addAttribute("templates", templates);
        model.addAttribute("canDeploy", true);
        model.addAttribute("projectName", projectName);
        logger.debug("DEPLOY: Added attributes to model for project: {}", projectName);
        return "deploy";
    }

    @PostMapping("/{projectName}/deploy")
    public String deployProject(
            @PathVariable String projectName,
            @org.springframework.web.bind.annotation.RequestParam(value = "module", defaultValue = "all") String module,
            RedirectAttributes redirectAttributes) {
        logger.info("DEPLOY: Starting deployment for project: {}", projectName);
        try {
            String message = deployService.deployProject(projectName, module);
            logger.info("DEPLOY: Deployment successful for project: {} module: {}. Message: {}", projectName, module, message);
            redirectAttributes.addFlashAttribute("message", message);
        } catch (Exception e) {
            logger.error("DEPLOY: Deployment failed for project: {}", projectName, e);
            redirectAttributes.addFlashAttribute("error",
                    " Deployment failed for " + projectName + ": \n" + e.getMessage());
        }
        return "redirect:/";
    }
}