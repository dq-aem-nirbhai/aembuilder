package com.aem.builder.controller;

import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.impl.AemProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AemProjectServiceImpl aemProjectService;

    @GetMapping("/")
    public String test(Model model) throws IOException {
        List<ProjectDetails> projects = aemProjectService.getAllProjects();
        model.addAttribute("projects", projects);
        return "dashboard";
    }

    @PostMapping("/import")
    public String importProject(@RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No file selected for upload.");
            return "redirect:/";
        }

        Path projectsDir = Paths.get("generated-projects");
        try {
            if (!Files.exists(projectsDir)) {
                Files.createDirectories(projectsDir);
            }

            String originalName = file.getOriginalFilename();
            String baseName = originalName == null ? "imported" : originalName.replaceAll("\\.zip$", "");
            Path targetDir = projectsDir.resolve(baseName);
            if (Files.exists(targetDir)) {
                targetDir = projectsDir.resolve(baseName + "-" + System.currentTimeMillis());
            }
            Files.createDirectories(targetDir);

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path newPath = targetDir.resolve(entry.getName()).normalize();
                    if (!newPath.startsWith(targetDir)) {
                        throw new IOException("Bad zip entry");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(newPath);
                    } else {
                        if (newPath.getParent() != null) {
                            Files.createDirectories(newPath.getParent());
                        }
                        Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            redirectAttributes.addFlashAttribute("message", "Project imported successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error importing project: " + e.getMessage());
        }
        return "redirect:/";
    }

}
