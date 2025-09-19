package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
public class ZipUtil {

    /**
     * Generate a ZIP of a directory
     * @param sourceDir the directory to zip
     * @return byte array of ZIP contents
     * @throws IOException
     */
    public static byte[] zipDirectory(Path sourceDir) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path relative = sourceDir.relativize(path);
                        ZipEntry entry = new ZipEntry(relative.toString().replace("\\", "/"));
                        try {
                            zos.putNextEntry(entry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                            log.info("[ZipUtil] Added file '{}'", entry.getName());
                        } catch (IOException e) {
                            throw new RuntimeException("[ZipUtil] Error zipping file " + entry.getName(), e);
                        }
                    });

            zos.finish();
            return baos.toByteArray();
        }
    }

    /**
     * Extract a ZIP file to a target directory
     * @param zipPath path to the ZIP file
     * @param targetDir target directory
     * @throws IOException
     */
    public static void extractZip(Path zipPath, Path targetDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryDest = targetDir.resolve(entry.getName()).normalize();

                // Prevent ZIP slip vulnerability
                if (!entryDest.startsWith(targetDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryDest);
                    log.info("[ZipUtil] Created directory '{}'", entryDest);
                } else {
                    Files.createDirectories(entryDest.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryDest, StandardCopyOption.REPLACE_EXISTING);
                        log.info("[ZipUtil] Extracted file '{}'", entryDest);
                    }
                }
            }
        }
    }

    /**
     * Extract artifactId from pom.xml inside a ZIP
     * @param zipPath path to the ZIP file
     * @return artifactId or null if not found
     * @throws IOException
     */
    public static String getArtifactIdFromZip(Path zipPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith("pom.xml")) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        return PomXmlUtil.parseArtifactId(is);
                    }
                }
            }
        }
        log.warn("[ZipUtil] No pom.xml found in ZIP '{}'", zipPath);
        return null;
    }

    /** Optional: check if a ZIP contains a file */
    public static boolean containsFile(Path zipPath, String fileName) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            return zipFile.stream().anyMatch(e -> e.getName().equals(fileName));
        }
    }
}
