package com.aem.builder.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;

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
}
