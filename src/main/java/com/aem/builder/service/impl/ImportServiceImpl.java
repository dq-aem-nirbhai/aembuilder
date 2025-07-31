package com.aem.builder.service.impl;

import com.aem.builder.exception.ProjectAlreadyExistsException;
import com.aem.builder.service.ImportService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ImportServiceImpl implements ImportService {

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public String importProject(MultipartFile file) throws IOException, ProjectAlreadyExistsException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are supported");
        }

        String projectName = extractRootFolderName(file);
        File destination = new File(PROJECTS_DIR, projectName);
        if (destination.exists()) {
            throw new ProjectAlreadyExistsException(projectName);
        }

        unzip(file.getInputStream(), new File(PROJECTS_DIR));
        return projectName;
    }

    private String extractRootFolderName(MultipartFile file) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("/")) {
                    return name.substring(0, name.indexOf('/'));
                } else {
                    return name;
                }
            }
        }
        throw new IllegalArgumentException("Empty zip file");
    }

    private void unzip(InputStream inputStream, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    if (!newFile.getParentFile().exists()) {
                        newFile.getParentFile().mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        zis.transferTo(fos);
                    }
                }
            }
        }
    }
}
