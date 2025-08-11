package com.aem.builder.controller;

import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;

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
    public String importProject(@RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes) {
        try {
            aemProjectService.importProject(file);
            redirectAttributes.addFlashAttribute("message", "Project imported successfully!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }


}
