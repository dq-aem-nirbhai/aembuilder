package com.aem.builder.controller;

import com.aem.builder.constants.UrlMappings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.IOException;

import static com.aem.builder.constants.UrlMappings.SHOW_FOLDER_URL;
import static com.aem.builder.constants.UrlMappings.DASHBOARD_REDIRECT;

@Controller
@Slf4j
public class FolderController {

    /**
     * Opens the folder at the given path using the OS default file explorer.
     *
     * @param path The folder path to open.
     * @return Redirects back to dashboard.
     */
    @GetMapping(SHOW_FOLDER_URL)
    public String openProjectFolder(@RequestParam String path) {
        log.info("[openProjectFolder] Request to open folder: {}", path);

        File folder = new File(path);

        if (!folder.exists()) {
            log.warn("[openProjectFolder] Folder not found: {}", path);
            return DASHBOARD_REDIRECT;
        }

        String os = System.getProperty("os.name").toLowerCase();
        Process process = null;

        try {
            if (os.contains("mac")) {
                log.info("[openProjectFolder] Detected macOS. Opening folder...");
                process = Runtime.getRuntime().exec(new String[]{"open", folder.getAbsolutePath()});
            } else if (os.contains("win")) {
                log.info("[openProjectFolder] Detected Windows. Opening folder...");
                process = Runtime.getRuntime().exec(new String[]{"explorer.exe", folder.getAbsolutePath()});
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                log.info("[openProjectFolder] Detected Linux/Unix. Opening folder...");
                process = Runtime.getRuntime().exec(new String[]{"xdg-open", folder.getAbsolutePath()});
            } else {
                log.error("[openProjectFolder] Unsupported OS: {}", os);
            }

            // Wait for process to complete
            if (process != null) {
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("[openProjectFolder] Folder opened successfully: {}", path);
                } else {
                    log.warn("[openProjectFolder] Folder open command exited with code: {}", exitCode);
                }
            }

        } catch (IOException e) {
            log.error("[openProjectFolder] IOException while opening folder '{}': {}", path, e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error("[openProjectFolder] Interrupted while opening folder '{}': {}", path, e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[openProjectFolder] Unexpected error while opening folder '{}': {}", path, e.getMessage(), e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
                log.info("[openProjectFolder] Destroyed folder open process for folder: {}", path);
            }
        }

        return DASHBOARD_REDIRECT;
    }
}
