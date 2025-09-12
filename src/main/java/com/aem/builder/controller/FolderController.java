package com.aem.builder.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;

@Controller
public class FolderController {

    @GetMapping("/show-folder")
    public String openProjectFolder(@RequestParam String path) {
        try {
            File folder = new File(path);
            if (folder.exists()) {
                String os = System.getProperty("os.name").toLowerCase();

                if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", folder.getAbsolutePath()});
                } else if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", folder.getAbsolutePath()});
                } else if (os.contains("nix") || os.contains("nux")) {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", folder.getAbsolutePath()});
                }
            } else {
                System.out.println("Folder not found: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/open-vscode")
    public String openInVSCode(@RequestParam String path, RedirectAttributes redirectAttributes) {
        try {
            File folder = new File(path);
            if (!folder.exists() || !folder.isDirectory()) {
                redirectAttributes.addFlashAttribute("error", "Invalid project folder: " + path);
                return "redirect:/dashboard";
            }

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "code", "-n", folder.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("code", "-n", folder.getAbsolutePath()).start(); // Mac/Linux
            }

            redirectAttributes.addFlashAttribute("message", "VS Code opened for " + folder.getName());
        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to open VS Code for " + path);
        }

        return "redirect:/dashboard";
    }

}
