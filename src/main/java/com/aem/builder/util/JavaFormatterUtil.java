package com.aem.builder.util;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;

public final class JavaFormatterUtil {

    /**
     * Cleans up a Java file by removing extra blank lines, aligning indentation,
     * and ensuring proper spacing between fields and methods.
     */
    public static void cleanAndFormatJavaFile(File javaFile) throws IOException {
        if (javaFile == null || !javaFile.exists()) return;

        String content = Files.readString(javaFile.toPath());

        // Remove leading/trailing blank lines
        content = content.replaceAll("(?m)^(\\s*\\r?\\n)+", "")
                .replaceAll("(\\r?\\n\\s*)+$", "\n");

        // Collapse 2+ blank lines to a single blank line
        content = content.replaceAll("(?m)(\\r?\\n){2,}", "\n\n");

        // Add blank line before methods (public/private/protected)
        content = content.replaceAll("(?m)(@\\w+\\s*\\r?\\n)?(\\s*)(public|private|protected)\\s+",
                "\n$1$2$3 ");

        // Remove trailing spaces
        content = content.replaceAll("[ \\t]+(?=\\r?\\n)", "");

        // Re-indent
        content = reindent(content);

        // Final trim and write
        Files.writeString(javaFile.toPath(), content.trim() + "\n");
    }

    /**
     * Basic indentation fix (lightweight)
     */
    private static String reindent(String content) {
        String[] lines = content.split("\\r?\\n");
        int indentLevel = 0;
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.endsWith("}")) indentLevel--;
            if (indentLevel < 0) indentLevel = 0;

            if (!trimmed.isEmpty()) {
                sb.append("    ".repeat(indentLevel)).append(trimmed);
            }
            sb.append("\n");

            if (trimmed.endsWith("{")) indentLevel++;
        }
        return sb.toString().lines().collect(Collectors.joining("\n"));
    }
}
