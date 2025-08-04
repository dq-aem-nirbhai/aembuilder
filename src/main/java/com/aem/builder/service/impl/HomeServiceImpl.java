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
     * Recursively search for a directory containing a pom.xml and ui.apps folder.
     */
    private File findAemProjectRoot(File directory) {
        if (directory == null) {
            return null;
        }
        File pom = new File(directory, "pom.xml");
        File uiApps = new File(directory, "ui.apps");
        if (pom.exists() && uiApps.isDirectory()) {
            return directory;
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
        return null;
    }
}

