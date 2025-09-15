package com.aem.builder.controller;

import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.AemProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.aem.builder.constants.UrlMappings.*;
import static com.aem.builder.constants.ViewNames.*;
import static com.aem.builder.constants.ModelAttributeKeys.*;
@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    // Injecting the service that handles AEM project operations
    private final AemProjectService aemProjectService;

    /**
     * Handles GET requests to the root URL ("/").
     * Loads the home page (index view).
     */
    @GetMapping("/")
    public String showIndex() {
        log.info("[showIndex] Loading home page");
        return INDEX_PAGE; // Returns the view name for the home page
    }

    /**
     * Handles GET requests to the dashboard URL.
     * Fetches all projects and adds them to the model for rendering.
     */
    @GetMapping(DASHBOARD_URL)
    public String showDashboard(Model model) {
        log.info("[showDashboard] Fetching all projects");
        List<ProjectDetails> projects = aemProjectService.getAllProjects(); // Fetch project list
        model.addAttribute(PROJECTS, projects); // Add projects to the model for Thymeleaf view
        return DASHBOARD_PAGE; // Return the dashboard view
    }

    /**
     * Handles GET requests to download a project as a ZIP.
     * @param projectName The name of the project to download
     * @return ResponseEntity containing the project ZIP
     */
    @GetMapping(DOWNLOAD_PROJECT_URL)
    public ResponseEntity<ByteArrayResource> downloadProject(@PathVariable String projectName) {
        log.info("[downloadProject] Download requested for project: {}", projectName);
        try {
            byte[] data = aemProjectService.downloadProjectZip(projectName); // Get ZIP bytes
            ByteArrayResource resource = new ByteArrayResource(data);

            // Return response with appropriate headers for download
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + projectName + ".zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);
        } catch (IOException e) {
            log.error("[downloadProject] Error downloading project {}: {}", projectName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build(); // Return 500 if error occurs
        }
    }

    /**
     * Handles POST requests to import a project via AJAX.
     * Accepts a multipart file, imports the project, and returns JSON response.
     */
    @PostMapping(IMPORT_PROJECT_URL)
    @ResponseBody // Indicates that the method returns JSON response
    public Map<String, Object> importProjectAjax(@RequestParam("file") MultipartFile file) {
        log.info("[importProjectAjax] Importing project file: {}", file.getOriginalFilename());
        Map<String, Object> response = new HashMap<>();
        try {
            aemProjectService.importProject(file); // Import the uploaded project
            response.put("success", true);
            response.put(MESSAGE, "Project imported successfully!");
        } catch (IOException e) {
            log.error("[importProjectAjax] Error importing project: {}", e.getMessage(), e);
            response.put("success", false);
            response.put(ERROR, e.getMessage());
        }
        return response;
    }

    /**
     * Handles POST requests to clone a project via AJAX.
     * Accepts a JSON payload containing the repository URL.
     */
    @PostMapping(CLONE_PROJECT_URL)
    @ResponseBody
    public Map<String, Object> cloneProjectAjax(@RequestBody Map<String, String> body) {
        String repoUrl = body.get("repoUrl");
        log.info("[cloneProjectAjax] Cloning project from URL: {}", repoUrl);

        Map<String, Object> response = new HashMap<>();
        try {
            aemProjectService.cloneProject(repoUrl); // Clone project from repo URL
            response.put("success", true);
            response.put(MESSAGE, "Project cloned successfully!");
        } catch (Exception e) {
            log.error("[cloneProjectAjax] Error cloning project: {}", e.getMessage(), e);
            response.put("success", false);
            response.put(ERROR, "Unexpected error: " + e.getMessage());
        }
        return response;
    }

    /**
     * Handles POST requests to validate a project before importing.
     * Checks if the project already exists and returns JSON result.
     */
    @PostMapping(VALIDATE_IMPORT_URL)
    @ResponseBody
    public Map<String, Object> validateImport(@RequestParam("file") MultipartFile file) {
        log.info("[validateImport] Validating project file: {}", file.getOriginalFilename());
        Map<String, Object> response = new HashMap<>();
        try {
            String artifactId = aemProjectService.extractArtifactId(file); // Extract artifactId from project

            if (aemProjectService.projectExists(artifactId)) {
                response.put("valid", false);
                response.put(ERROR, "A project with artifactId '" + artifactId + "' already exists.");
            } else {
                response.put("valid", true);
            }
        } catch (Exception e) {
            log.error("[validateImport] Error validating project: {}", e.getMessage(), e);
            response.put("valid", false);
            response.put(ERROR, "Invalid project ZIP: " + e.getMessage());
        }
        return response;
    }

    /**
     * Handles POST requests to delete a project.
     * @param projectName The project to delete
     * @param redirectAttributes Flash attributes for redirect messages
     * @return Redirects back to dashboard
     */
    @PostMapping(DELETE_PROJECT_URL)
    public String deleteProject(@PathVariable String projectName, RedirectAttributes redirectAttributes) {
        log.info("[deleteProject] Deleting project: {}", projectName);
        try {
            aemProjectService.deleteProject(projectName); // Delete project from file system
            redirectAttributes.addFlashAttribute(MESSAGE, projectName + " Project deleted successfully!");
        } catch (IOException e) {
            log.error("[deleteProject] Error deleting project {}: {}", projectName, e.getMessage(), e);
            redirectAttributes.addFlashAttribute(ERROR, "Error deleting project: " + e.getMessage());
        }
        return DASHBOARD_REDIRECT; // Redirect to dashboard page
    }
}
