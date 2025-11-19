package com.aem.builder.controller;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.service.ComponentService;
import com.aem.builder.service.TemplateService;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.aem.builder.constants.UrlMappings.*;
import static com.aem.builder.constants.ViewNames.*;
import static com.aem.builder.constants.ModelAttributeKeys.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AemProjectController {

    private final AemProjectServiceImpl aemProjectService;
    private final TemplateService templateService;
    private final ComponentService componentService;

    /**
     * Show project creation page with templates and components.
     */
    @GetMapping(CREATE_PROJECT_URL)
    public String createProject(Model model) throws IOException {
        log.info("[CREATE_PROJECT] Navigating to project creation page");

        model.addAttribute(AEM_PROJECT_MODEL, new AemProjectModel());
        model.addAttribute(COMPONENT_LIST, componentService.getAllComponents());

        log.info("[CREATE_PROJECT] Loaded {} components", componentService.getAllComponents().size());
        return CREATE_PAGE;
    }

    /**
     * Check if project name already exists (AJAX call).
     */
    @GetMapping(CHECK_PROJECT_NAME_URL)
    @ResponseBody
    public Map<String, Object> checkProjectName(@RequestParam String name) {
        boolean exists = aemProjectService.projectExists(name);
        log.info("[CHECK_PROJECT_NAME] Checking project name availability for '{}'. Exists? {}", name, exists);

        Map<String, Object> response = new HashMap<>();
        response.put("exists", exists);
        response.put("code", exists ? "PROJECT_EXISTS" : "PROJECT_AVAILABLE");
        response.put(MESSAGE, exists ? "Project already exists" : "Project name is available");

        log.info("[CHECK_PROJECT_NAME] Response: {}", response);
        return response;
    }

    /**
     * Save and generate a new AEM project.
     */
    @PostMapping(value = {SAVE_PROJECT_URL, "/save"})
    public String saveConfig(@ModelAttribute AemProjectModel aemProjectModel,
                             @RequestParam(value = "lombokEnabled", required = false) String lombokFlag,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        log.info("[SAVE_PROJECT] Received request to create project '{}'", aemProjectModel.getProjectName());

        try {
            boolean lombok = lombokFlag != null;
            aemProjectService.generateProject(aemProjectModel, lombok);

            String successMessage = aemProjectModel.getProjectName() + " Project created successfully.";
            redirectAttributes.addFlashAttribute(MESSAGE, successMessage);

            log.info("[SAVE_PROJECT] Project '{}' created successfully", aemProjectModel.getProjectName());
            return DASHBOARD_REDIRECT;

        } catch (IOException e) {
            log.info("[SAVE_PROJECT] Error while creating project '{}': {}", aemProjectModel.getProjectName(), e.getMessage());

            model.addAttribute(ERROR, e.getMessage());
            try {
                List<String> templates = templateService.getTemplateFileNames();
                model.addAttribute(TEMPLATES, templates);
                model.addAttribute(COMPONENT_LIST, componentService.getAllComponents());
                log.info("[SAVE_PROJECT] Reloaded templates/components after error");
            } catch (IOException ignored) {
                log.info("[SAVE_PROJECT] Failed to reload templates/components after error");
            }

            return CREATE_PAGE;
        }
    }
}
