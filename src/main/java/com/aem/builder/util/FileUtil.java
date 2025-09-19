package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FileUtil {

    /** Create directories if not exist */
    public static Path createDirectories(String... paths) throws IOException {
        Path path = Paths.get("", paths);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("[FileUtil] Created directory '{}'", path);
        } else {
            log.info("[FileUtil] Directory already exists '{}'", path);
        }
        return path;
    }

    /** Move project directory or copy if atomic move fails (cross-filesystem) */
    public static void moveOrCopyProject(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("[FileUtil] Project moved to '{}'", target);
        } catch (IOException e) {
            FileUtils.copyDirectory(source.toFile(), target.toFile());
            FileUtils.deleteDirectory(source.toFile());
            log.info("[FileUtil] Project copied to '{}' across filesystems", target);
        }
    }

    /** Cleanup temporary directory */
    public static void cleanupTemp(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                FileUtils.deleteDirectory(tempDir.toFile());
                log.info("[FileUtil] Cleaned up temporary directory '{}'", tempDir);
            }
        } catch (IOException e) {
            log.warn("[FileUtil] Failed to clean temp directory '{}': {}", tempDir, e.getMessage(), e);
        }
    }


    /**
     * Ensures a directory exists; creates it if missing.
     *
     * @param folder Directory
     */
    public static void ensureFolder(File folder) {
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            log.info("{} Created folder '{}'", folder.getAbsolutePath(), created);
        }
    }

    /**
     * Recursively walks a base directory and collects files matching a filename.
     *
     * @param baseDir      Base directory
     * @param filenameMatch Filename to match
     * @return List of matching files
     */
    public static List<File> walkFiles(File baseDir, String filenameMatch) {
        List<File> files = new ArrayList<>();
        if (!baseDir.exists()) return files;

        try {
            Files.walk(baseDir.toPath())
                    .filter(path -> path.getFileName().toString().equals(filenameMatch))
                    .forEach(path -> files.add(path.toFile()));
        } catch (IOException e) {
            log.info("{} Error walking files in", baseDir.getAbsolutePath(), e);
        }
        return files;
    }

}
