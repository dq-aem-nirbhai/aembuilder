package com.aem.builder.controller;

import com.aem.builder.exception.ProjectAlreadyExistsException;
import com.aem.builder.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/import")
    public String importFromLocal(@RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        try {
            String projectName = importService.importProject(file);
            redirectAttributes.addFlashAttribute("message",
                    "Imported project: " + projectName);
        } catch (ProjectAlreadyExistsException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException | IOException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }
}
