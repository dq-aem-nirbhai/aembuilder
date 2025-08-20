package com.aem.builder.controller;

import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
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

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AemProjectServiceImpl aemProjectService;

    @GetMapping("/")
    public String index(Model model)  {

        return "indexpage";
    }

    @GetMapping("/dashboard")
    public String test(Model model) throws IOException {
        List<ProjectDetails> projects = aemProjectService.getAllProjects();
        model.addAttribute("projects", projects);
        return "dashboard";
    }

    @GetMapping("/download/{projectName}")
    public ResponseEntity<ByteArrayResource> downloadProject(@PathVariable String projectName) throws IOException {
        byte[] data = aemProjectService.getProjectZip(projectName);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + projectName + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    }

    @PostMapping("/import")
    @ResponseBody
    public Map<String, Object> importProjectAjax(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            aemProjectService.importProject(file);
            response.put("success", true);
            response.put("message", "Project imported successfully!");
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/validateImport")
    @ResponseBody
    public Map<String, Object> validateImport(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            String artifactId = aemProjectService.extractArtifactId(file);

            if (aemProjectService.projectExists(artifactId)) {
                response.put("valid", false);
                response.put("error", "A project with artifactId '" + artifactId + "' already exists.");
            } else {
                response.put("valid", true);
            }
        } catch (Exception e) {
            response.put("valid", false);
            response.put("error", "Invalid project ZIP: " + e.getMessage());
        }
        return response;
    }



    @PostMapping("/delete/{projectName}")

    public String deleteProject(@PathVariable String projectName, RedirectAttributes redirectAttributes) {
        try {
            aemProjectService.deleteProject(projectName);
            redirectAttributes.addFlashAttribute("message", projectName + " Project deleted successfully!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting project: " + e.getMessage());
        }
        return "redirect:/dashboard";
}


}
