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
import java.util.ArrayList;
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
        List<String> templates = templateService.fetchTemplatesFromGeneratedProjects(projectName);
        var compMap = componentService.fetchComponentsWithGroups(projectName);
        List<String> components = new ArrayList<>(compMap.keySet());
        List<String> editable = compMap.entrySet().stream()
                .filter(e -> {
                    String g = e.getValue();
                    return g == null || (!g.equals(projectName + " - Content")
                            && !g.equals(projectName + " - Structure")
                            && !g.equals(".hidden"));
                })
                .map(java.util.Map.Entry::getKey)
                .toList();
        model.addAttribute("components", components);
        model.addAttribute("editableComponents", editable);
        model.addAttribute("templates", templates);
        model.addAttribute("projectName", projectName);
        model.addAttribute("canDeploy", true);
        logger.debug("DEPLOY: Added attributes to model for project: {}", projectName);
        return "deploy";
    }

    @PostMapping("/{projectName}/deploy")
    public String deployProject(@PathVariable String projectName, RedirectAttributes redirectAttributes) {
        logger.info("DEPLOY: Starting deployment for project: {}", projectName);
        try {
            String message = deployService.deployProject(projectName);
            logger.info("DEPLOY: Deployment successful for project: {}. Message: {}", projectName, message);
            redirectAttributes.addFlashAttribute("message", message);
        } catch (Exception e) {
            logger.error("DEPLOY: Deployment failed for project: {}", projectName, e);
            redirectAttributes.addFlashAttribute("error",
                    " Deployment failed for " + projectName + ": \n" + e.getMessage());
        }
        return "redirect:/dashboard";
    }
}