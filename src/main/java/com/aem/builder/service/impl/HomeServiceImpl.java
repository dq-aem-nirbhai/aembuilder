package com.aem.builder.service.impl;

import com.aem.builder.service.HomeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Implementation of {@link HomeService} handling dashboard related operations.
 */
@Service
@Slf4j
public class HomeServiceImpl implements HomeService {

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public void importProject(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("This is not an AEM structure file");
        }

        Path tempDir = Files.createTempDirectory("aem-import");
        try {
            Path zipPath = tempDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), zipPath, StandardCopyOption.REPLACE_EXISTING);

            Path extractDir = tempDir.resolve("extracted");
            Files.createDirectories(extractDir);
            unzip(zipPath, extractDir);

            File root = findAemProjectRoot(extractDir.toFile());
            if (root == null) {
                throw new IllegalArgumentException("This is not an AEM structure file");
            }

            Path projectsDir = Paths.get(PROJECTS_DIR);
            Files.createDirectories(projectsDir);

            String projectName = root.equals(extractDir.toFile())
                    ? file.getOriginalFilename().replaceFirst("\\.zip$", "")
                    : root.getName();
            Path target = projectsDir.resolve(projectName);
            if (Files.exists(target)) {
                throw new IllegalArgumentException("Project already exists");
            }
            Files.move(root.toPath(), target);

        } finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    private void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName()).normalize();
                if (!newPath.startsWith(targetDir)) {
                    throw new IOException("Bad zip entry");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Locate the deepest directory that matches the expected AEM project structure.
     */
    private File findAemProjectRoot(File directory) {
        if (directory == null) {
            return null;
        }

        File[] children = directory.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                File result = findAemProjectRoot(child);
                if (result != null) {
                    return result;
                }
            }
        }

        return isValidAemStructure(directory) ? directory : null;
    }

    /**
     * Validate that the directory contains the required AEM project modules and files.
     */
    private boolean isValidAemStructure(File directory) {
        String[] requiredDirs = {
                "all", "core", "dispatcher", "it.tests", "ui.apps",
                "ui.config", "ui.content", "ui.frontend", "ui.tests"
        };
        String[] requiredFiles = {"pom.xml", "archetype.properties", "readme.md", "license"};

        for (String dir : requiredDirs) {
            if (!hasChild(directory, dir, true)) {
                return false;
            }
        }
        if (!(hasChild(directory, "ui.apps.structure", true) || hasChild(directory, "ui.apps.structured", true))) {
            return false;
        }
        for (String file : requiredFiles) {
            if (file.equals("license")) {
                if (!(hasChild(directory, "license", false) || hasChild(directory, "LICENSE", false))) {
                    return false;
                }
            } else if (!hasChild(directory, file, false)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasChild(File directory, String name, boolean expectDirectory) {
        File[] children = directory.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.getName().equalsIgnoreCase(name)) {
                return expectDirectory ? child.isDirectory() : child.isFile();
            }
        }
        return false;
    }
}

