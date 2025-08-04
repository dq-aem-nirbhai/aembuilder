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

            File[] contents = extractDir.toFile().listFiles();
            if (contents == null || contents.length == 0) {
                throw new IllegalArgumentException("This is not an AEM structure file");
            }
            File root = (contents.length == 1 && contents[0].isDirectory()) ? contents[0] : extractDir.toFile();

            if (!(new File(root, "pom.xml").exists() && new File(root, "ui.apps").isDirectory())) {
                throw new IllegalArgumentException("This is not an AEM structure file");
            }

            Path projectsDir = Paths.get(PROJECTS_DIR);
            Files.createDirectories(projectsDir);
            Path target = projectsDir.resolve(root.getName());
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
}

